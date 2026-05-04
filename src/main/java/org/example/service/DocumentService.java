package org.example.service;

import org.apache.lucene.queryparser.classic.ParseException;
import org.example.exception.DocumentNotFoundException;
import org.example.model.Document;
import org.example.model.DocumentRequest;
import org.example.model.SearchResult;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {

    private final LuceneSearchService luceneSearchService;
    private final RateLimiterService rateLimiterService;
    private final Cache searchCache;

    // Tenant-isolated in-memory document store: tenantId -> (docId -> Document)
    private final Map<String, Map<String, Document>> store = new ConcurrentHashMap<>();

    public DocumentService(LuceneSearchService luceneSearchService,
                           RateLimiterService rateLimiterService,
                           CacheManager cacheManager) {
        this.luceneSearchService = luceneSearchService;
        this.rateLimiterService = rateLimiterService;
        this.searchCache = cacheManager.getCache("searchResults");
    }

    public Document indexDocument(String tenantId, DocumentRequest request) {
        rateLimiterService.checkRateLimit(tenantId);

        Document doc = Document.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .title(request.getTitle())
                .content(request.getContent())
                .metadata(request.getMetadata())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            luceneSearchService.indexDocument(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index document", e);
        }

        store.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>()).put(doc.getId(), doc);

        // Invalidate all cached search results for this tenant on write
        if (searchCache != null) searchCache.clear();

        return doc;
    }

    public Document getDocument(String tenantId, String documentId) {
        rateLimiterService.checkRateLimit(tenantId);

        Document doc = store.getOrDefault(tenantId, Map.of()).get(documentId);
        if (doc == null) throw new DocumentNotFoundException(documentId);
        return doc;
    }

    public void deleteDocument(String tenantId, String documentId) {
        rateLimiterService.checkRateLimit(tenantId);

        Map<String, Document> tenantStore = store.get(tenantId);
        if (tenantStore == null || !tenantStore.containsKey(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }

        try {
            luceneSearchService.deleteDocument(tenantId, documentId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete document from index", e);
        }

        tenantStore.remove(documentId);
        if (searchCache != null) searchCache.clear();
    }

    public SearchResult search(String tenantId, String query, int page, int pageSize) throws ParseException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        if (page < 0) throw new IllegalArgumentException("Page must be >= 0");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be between 1 and 100");

        rateLimiterService.checkRateLimit(tenantId);

        String cacheKey = tenantId + ":" + query + ":" + page + ":" + pageSize;
        if (searchCache != null) {
            SearchResult cached = searchCache.get(cacheKey, SearchResult.class);
            if (cached != null) return cached;
        }

        long start = System.currentTimeMillis();
        LuceneSearchService.SearchQueryResult result;
        try {
            result = luceneSearchService.search(tenantId, query, page, pageSize);
        } catch (IOException e) {
            throw new RuntimeException("Search failed due to index error", e);
        }

        // Enrich hits with metadata from the in-memory store
        Map<String, Document> tenantStore = store.getOrDefault(tenantId, Map.of());
        result.hits().forEach(hit -> {
            Document doc = tenantStore.get(hit.getId());
            if (doc != null) hit.setMetadata(doc.getMetadata());
        });

        SearchResult searchResult = SearchResult.builder()
                .hits(result.hits())
                .total(result.total())
                .query(query)
                .tenantId(tenantId)
                .tookMs(System.currentTimeMillis() - start)
                .page(page)
                .pageSize(pageSize)
                .build();

        if (searchCache != null) searchCache.put(cacheKey, searchResult);

        return searchResult;
    }
}
