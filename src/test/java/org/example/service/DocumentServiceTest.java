package org.example.service;

import org.apache.lucene.queryparser.classic.ParseException;
import org.example.exception.DocumentNotFoundException;
import org.example.exception.RateLimitExceededException;
import org.example.model.Document;
import org.example.model.DocumentRequest;
import org.example.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock LuceneSearchService luceneSearchService;
    @Mock RateLimiterService rateLimiterService;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    DocumentService documentService;

    @BeforeEach
    void setUp() {
        when(cacheManager.getCache("searchResults")).thenReturn(cache);
        documentService = new DocumentService(luceneSearchService, rateLimiterService, cacheManager);
    }

    private DocumentRequest req(String title, String content) {
        DocumentRequest r = new DocumentRequest();
        r.setTitle(title);
        r.setContent(content);
        return r;
    }

    // --- indexDocument ---

    @Test
    void indexDocument_returnsDocumentWithGeneratedUUID() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());

        Document doc = documentService.indexDocument("t1", req("Title", "Content"));

        assertThat(doc.getId()).isNotNull().isNotBlank();
        assertThat(doc.getId()).matches("[0-9a-f-]{36}"); // UUID format
    }

    @Test
    void indexDocument_setsCorrectTenantAndFields() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());

        Document doc = documentService.indexDocument("my-tenant", req("My Title", "My Content"));

        assertThat(doc.getTenantId()).isEqualTo("my-tenant");
        assertThat(doc.getTitle()).isEqualTo("My Title");
        assertThat(doc.getContent()).isEqualTo("My Content");
    }

    @Test
    void indexDocument_setsCreatedAtAndUpdatedAt() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());

        Document doc = documentService.indexDocument("t1", req("T", "C"));

        assertThat(doc.getCreatedAt()).isNotNull();
        assertThat(doc.getUpdatedAt()).isNotNull();
    }

    @Test
    void indexDocument_passesDocumentToLuceneService() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());

        documentService.indexDocument("t1", req("Title", "Content"));

        verify(luceneSearchService).indexDocument(argThat(d ->
                "t1".equals(d.getTenantId()) && "Title".equals(d.getTitle())
        ));
    }

    @Test
    void indexDocument_checksRateLimitBeforeAnyProcessing() throws IOException {
        doThrow(new RateLimitExceededException("t1")).when(rateLimiterService).checkRateLimit("t1");

        assertThatThrownBy(() -> documentService.indexDocument("t1", req("T", "C")))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(luceneSearchService);
    }

    @Test
    void indexDocument_invalidatesSearchCacheOnWrite() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());

        documentService.indexDocument("t1", req("T", "C"));

        verify(cache).clear();
    }

    @Test
    void indexDocument_wrapsIOExceptionAsRuntimeException() throws IOException {
        doThrow(new IOException("disk full")).when(luceneSearchService).indexDocument(any());

        assertThatThrownBy(() -> documentService.indexDocument("t1", req("T", "C")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to index");
    }

    // --- getDocument ---

    @Test
    void getDocument_returnsCorrectDocumentForTenant() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());
        Document indexed = documentService.indexDocument("t1", req("My Doc", "body"));

        Document found = documentService.getDocument("t1", indexed.getId());

        assertThat(found.getId()).isEqualTo(indexed.getId());
        assertThat(found.getTitle()).isEqualTo("My Doc");
    }

    @Test
    void getDocument_throwsNotFoundForMissingId() {
        assertThatThrownBy(() -> documentService.getDocument("t1", "nonexistent-id"))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("nonexistent-id");
    }

    @Test
    void getDocument_tenantIsolation_throwsNotFoundForOtherTenant() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());
        Document doc = documentService.indexDocument("tenant-a", req("Secret", "data"));

        assertThatThrownBy(() -> documentService.getDocument("tenant-b", doc.getId()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getDocument_checksRateLimit() {
        doThrow(new RateLimitExceededException("t1")).when(rateLimiterService).checkRateLimit("t1");

        assertThatThrownBy(() -> documentService.getDocument("t1", "any-id"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // --- deleteDocument ---

    @Test
    void deleteDocument_removesDocumentFromInMemoryStore() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());
        doNothing().when(luceneSearchService).deleteDocument(anyString(), anyString());
        Document doc = documentService.indexDocument("t1", req("T", "C"));

        documentService.deleteDocument("t1", doc.getId());

        assertThatThrownBy(() -> documentService.getDocument("t1", doc.getId()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void deleteDocument_callsLuceneDelete() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());
        doNothing().when(luceneSearchService).deleteDocument(anyString(), anyString());
        Document doc = documentService.indexDocument("t1", req("T", "C"));

        documentService.deleteDocument("t1", doc.getId());

        verify(luceneSearchService).deleteDocument("t1", doc.getId());
    }

    @Test
    void deleteDocument_throwsNotFoundForMissingDocument() {
        assertThatThrownBy(() -> documentService.deleteDocument("t1", "ghost-id"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void deleteDocument_invalidatesSearchCache() throws IOException {
        doNothing().when(luceneSearchService).indexDocument(any());
        doNothing().when(luceneSearchService).deleteDocument(anyString(), anyString());
        Document doc = documentService.indexDocument("t1", req("T", "C"));
        clearInvocations(cache); // reset count after indexDocument's clear()

        documentService.deleteDocument("t1", doc.getId());

        verify(cache).clear();
    }

    // --- search ---

    @Test
    void search_throwsIllegalArgumentForBlankQuery() {
        assertThatThrownBy(() -> documentService.search("t1", "  ", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void search_throwsIllegalArgumentForNullQuery() {
        assertThatThrownBy(() -> documentService.search("t1", null, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_throwsIllegalArgumentForNegativePage() {
        assertThatThrownBy(() -> documentService.search("t1", "query", -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_throwsIllegalArgumentForPageSizeTooSmall() {
        assertThatThrownBy(() -> documentService.search("t1", "query", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_throwsIllegalArgumentForPageSizeTooLarge() {
        assertThatThrownBy(() -> documentService.search("t1", "query", 0, 200))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_returnsCachedResultOnCacheHit() throws Exception {
        SearchResult cached = SearchResult.builder().query("q").hits(List.of()).total(5).build();
        when(cache.get("t1:q:0:10", SearchResult.class)).thenReturn(cached);

        SearchResult result = documentService.search("t1", "q", 0, 10);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(luceneSearchService);
    }

    @Test
    void search_onCacheMiss_queriesLuceneAndPopulatesCache() throws Exception {
        when(cache.get(anyString(), eq(SearchResult.class))).thenReturn(null);
        when(luceneSearchService.search("t1", "query", 0, 10))
                .thenReturn(new LuceneSearchService.SearchQueryResult(List.of(), 0));

        documentService.search("t1", "query", 0, 10);

        verify(luceneSearchService).search("t1", "query", 0, 10);
        verify(cache).put(eq("t1:query:0:10"), any(SearchResult.class));
    }

    @Test
    void search_resultContainsQueryMetadata() throws Exception {
        when(cache.get(anyString(), eq(SearchResult.class))).thenReturn(null);
        when(luceneSearchService.search("t1", "test", 2, 5))
                .thenReturn(new LuceneSearchService.SearchQueryResult(List.of(), 0));

        SearchResult result = documentService.search("t1", "test", 2, 5);

        assertThat(result.getQuery()).isEqualTo("test");
        assertThat(result.getTenantId()).isEqualTo("t1");
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
        assertThat(result.getTookMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void search_checksRateLimitBeforeSearch() throws ParseException {
        doThrow(new RateLimitExceededException("t1")).when(rateLimiterService).checkRateLimit("t1");

        assertThatThrownBy(() -> documentService.search("t1", "query", 0, 10))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(luceneSearchService);
    }
}
