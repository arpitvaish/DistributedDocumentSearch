package org.example.service;

import org.example.model.Document;
import org.example.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class LuceneSearchServiceTest {

    private LuceneSearchService service;

    @BeforeEach
    void setUp() {
        service = new LuceneSearchService();
    }

    private Document doc(String id, String tenantId, String title, String content) {
        return Document.builder()
                .id(id).tenantId(tenantId).title(title).content(content)
                .createdAt(LocalDateTime.now()).build();
    }

    // --- Indexing ---

    @Test
    void indexDocument_addsDocumentToIndex() throws Exception {
        service.indexDocument(doc("d1", "t1", "Annual Report", "revenue expenses"));

        var result = service.search("t1", "revenue", 0, 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hits()).extracting(SearchResult.Hit::getId).containsExactly("d1");
    }

    @Test
    void indexDocument_upsert_updatesExistingDocument() throws Exception {
        service.indexDocument(doc("d1", "t1", "Original Title", "original content"));
        service.indexDocument(doc("d1", "t1", "Updated Title", "updated content"));

        var result = service.search("t1", "Updated", 0, 10);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hits().get(0).getTitle()).isEqualTo("Updated Title");

        // Old content must not appear
        var old = service.search("t1", "Original", 0, 10);
        assertThat(old.total()).isZero();
    }

    // --- Deletion ---

    @Test
    void deleteDocument_removesDocumentFromSearchResults() throws Exception {
        service.indexDocument(doc("d1", "t1", "Report", "content"));
        service.deleteDocument("t1", "d1");

        var result = service.search("t1", "Report", 0, 10);
        assertThat(result.total()).isZero();
    }

    @Test
    void deleteDocument_onlyRemovesTargetDocument() throws Exception {
        service.indexDocument(doc("d1", "t1", "Keep this", "important"));
        service.indexDocument(doc("d2", "t1", "Delete this", "important"));
        service.deleteDocument("t1", "d2");

        var result = service.search("t1", "important", 0, 10);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hits().get(0).getId()).isEqualTo("d1");
    }

    // --- Multi-tenant isolation ---

    @Test
    void search_strictlyIsolatesByTenant() throws Exception {
        service.indexDocument(doc("da", "tenant-a", "Confidential A", "private info"));
        service.indexDocument(doc("db", "tenant-b", "Confidential B", "private info"));

        var resultA = service.search("tenant-a", "private", 0, 10);
        var resultB = service.search("tenant-b", "private", 0, 10);

        assertThat(resultA.hits()).extracting(SearchResult.Hit::getId).containsExactly("da");
        assertThat(resultB.hits()).extracting(SearchResult.Hit::getId).containsExactly("db");
    }

    @Test
    void search_emptyResultForNewTenantWithNoDocuments() throws Exception {
        var result = service.search("brand-new-tenant", "anything", 0, 10);

        assertThat(result.total()).isZero();
        assertThat(result.hits()).isEmpty();
    }

    // --- Search field matching ---

    @Test
    void search_matchesTitleField() throws Exception {
        service.indexDocument(doc("d1", "t1", "Machine Learning Fundamentals", "intro text"));

        var result = service.search("t1", "title:Machine", 0, 10);
        assertThat(result.hits()).extracting(SearchResult.Hit::getId).contains("d1");
    }

    @Test
    void search_matchesContentField() throws Exception {
        service.indexDocument(doc("d1", "t1", "Generic Title", "distributed systems architecture patterns"));

        var result = service.search("t1", "content:distributed", 0, 10);
        assertThat(result.hits()).extracting(SearchResult.Hit::getId).contains("d1");
    }

    @Test
    void search_returnsEmptyForNoMatch() throws Exception {
        service.indexDocument(doc("d1", "t1", "Java Programming", "object oriented design"));

        var result = service.search("t1", "python", 0, 10);
        assertThat(result.total()).isZero();
    }

    // --- Boolean / advanced queries ---

    @Test
    void search_booleanAndQuery_narrowsResults() throws Exception {
        service.indexDocument(doc("d1", "t1", "Spring Boot App", "java web framework"));
        service.indexDocument(doc("d2", "t1", "Django App", "python web framework"));

        var result = service.search("t1", "java AND framework", 0, 10);
        assertThat(result.hits()).extracting(SearchResult.Hit::getId).containsExactly("d1");
    }

    @Test
    void search_wildcardQuery_matchesPrefix() throws Exception {
        service.indexDocument(doc("d1", "t1", "Microservices Architecture", "design patterns"));

        var result = service.search("t1", "Microservice*", 0, 10);
        assertThat(result.hits()).extracting(SearchResult.Hit::getId).contains("d1");
    }

    // --- Pagination ---

    @Test
    void search_paginatesCorrectly() throws Exception {
        for (int i = 1; i <= 5; i++) {
            service.indexDocument(doc("d" + i, "t1", "Doc " + i, "paginationkeyword content"));
        }

        var page0 = service.search("t1", "paginationkeyword", 0, 2);
        var page1 = service.search("t1", "paginationkeyword", 1, 2);
        var page2 = service.search("t1", "paginationkeyword", 2, 2);

        assertThat(page0.total()).isEqualTo(5);
        assertThat(page0.hits()).hasSize(2);
        assertThat(page1.hits()).hasSize(2);
        assertThat(page2.hits()).hasSize(1); // last page

        var ids0 = page0.hits().stream().map(SearchResult.Hit::getId).toList();
        var ids1 = page1.hits().stream().map(SearchResult.Hit::getId).toList();
        assertThat(ids0).doesNotContainAnyElementsOf(ids1);
    }

    @Test
    void search_multipleDocuments_returnsAllHits() throws Exception {
        service.indexDocument(doc("d1", "t1", "Spring Boot Guide", "microservices framework"));
        service.indexDocument(doc("d2", "t1", "Spring Security", "auth framework"));
        service.indexDocument(doc("d3", "t1", "Hibernate ORM", "database framework"));

        var result = service.search("t1", "framework", 0, 10);
        assertThat(result.total()).isEqualTo(3);
    }

    // --- Existence checks ---

    @Test
    void documentExists_returnsTrueAfterIndexing() throws Exception {
        service.indexDocument(doc("d1", "t1", "title", "content"));
        assertThat(service.documentExists("t1", "d1")).isTrue();
    }

    @Test
    void documentExists_returnsFalseForUnknownId() throws Exception {
        assertThat(service.documentExists("t1", "ghost-doc")).isFalse();
    }

    @Test
    void documentExists_returnsFalseAfterDeletion() throws Exception {
        service.indexDocument(doc("d1", "t1", "title", "content"));
        service.deleteDocument("t1", "d1");
        assertThat(service.documentExists("t1", "d1")).isFalse();
    }

    // --- Counts ---

    @Test
    void getDocumentCount_returnsCorrectCountPerTenant() throws Exception {
        service.indexDocument(doc("d1", "t1", "a", "b"));
        service.indexDocument(doc("d2", "t1", "c", "d"));
        service.indexDocument(doc("d3", "t2", "e", "f"));

        assertThat(service.getDocumentCount("t1")).isEqualTo(2);
        assertThat(service.getDocumentCount("t2")).isEqualTo(1);
    }

    @Test
    void getTotalIndexedDocuments_sumsAcrossAllTenants() throws Exception {
        service.indexDocument(doc("d1", "ta", "a", "b"));
        service.indexDocument(doc("d2", "tb", "c", "d"));
        service.indexDocument(doc("d3", "tc", "e", "f"));

        assertThat(service.getTotalIndexedDocuments()).isEqualTo(3);
    }

    @Test
    void getIndexedTenantCount_returnsNumberOfDistinctTenants() throws Exception {
        service.indexDocument(doc("d1", "ta", "a", "b"));
        service.indexDocument(doc("d2", "ta", "c", "d")); // same tenant
        service.indexDocument(doc("d3", "tb", "e", "f")); // new tenant

        assertThat(service.getIndexedTenantCount()).isEqualTo(2);
    }
}
