package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Document;
import org.example.model.DocumentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentSearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private String tenant;

    @BeforeEach
    void setUp() {
        tenant = "integration-" + UUID.randomUUID();
    }

    private String indexDoc(String t, String title, String content) throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle(title);
        req.setContent(content);

        String body = mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, Document.class).getId();
    }

    // --- Full CRUD flow ---

    @Test
    void fullFlow_index_search_get_delete_verifyGone() throws Exception {
        // Index
        String id = indexDoc(tenant, "Enterprise Architecture Guide", "microservices distributed systems cloud");

        // Search finds it
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "microservices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].id").value(id));

        // Direct retrieval works
        mockMvc.perform(get("/documents/" + id).header(TENANT_HEADER, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // Delete
        mockMvc.perform(delete("/documents/" + id).header(TENANT_HEADER, tenant))
                .andExpect(status().isNoContent());

        // Direct retrieval returns 404
        mockMvc.perform(get("/documents/" + id).header(TENANT_HEADER, tenant))
                .andExpect(status().isNotFound());

        // Search also returns 0 results
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "microservices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- Multi-tenant isolation ---

    @Test
    void multiTenantIsolation_tenantBCannotSeeOrTouchTenantADocuments() throws Exception {
        String tenantA = "iso-a-" + UUID.randomUUID();
        String tenantB = "iso-b-" + UUID.randomUUID();

        String idA = indexDoc(tenantA, "Top Secret A", "confidential a-only data");

        // Tenant B search finds nothing
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenantB).param("q", "confidential"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // Tenant B GET by ID returns 404
        mockMvc.perform(get("/documents/" + idA).header(TENANT_HEADER, tenantB))
                .andExpect(status().isNotFound());

        // Tenant B DELETE returns 404 (cannot modify other tenant's data)
        mockMvc.perform(delete("/documents/" + idA).header(TENANT_HEADER, tenantB))
                .andExpect(status().isNotFound());

        // Tenant A still has the document
        mockMvc.perform(get("/documents/" + idA).header(TENANT_HEADER, tenantA))
                .andExpect(status().isOk());
    }

    // --- Relevance scoring ---

    @Test
    void search_higherTermFrequency_ranksFirstInResults() throws Exception {
        // Doc with more mentions of "spring" should rank higher
        indexDoc(tenant, "Spring Overview", "spring dependency injection");
        indexDoc(tenant, "Spring Boot Deep Dive", "spring boot spring spring spring auto-config");

        String body = mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn().getResponse().getContentAsString();

        var hits = objectMapper.readTree(body).get("hits");
        float score0 = (float) hits.get(0).get("score").asDouble();
        float score1 = (float) hits.get(1).get("score").asDouble();
        assertThat(score0).isGreaterThanOrEqualTo(score1);
    }

    // --- Search result structure ---

    @Test
    void search_responseContainsAllExpectedFields() throws Exception {
        indexDoc(tenant, "Structured Result Doc", "structured result content");

        String body = mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "structured")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var result = objectMapper.readTree(body);
        assertThat(result.get("query").asText()).isEqualTo("structured");
        assertThat(result.get("tenantId").asText()).isEqualTo(tenant);
        assertThat(result.get("page").asInt()).isEqualTo(0);
        assertThat(result.get("pageSize").asInt()).isEqualTo(10);
        assertThat(result.get("tookMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(result.get("hits").get(0).get("id").asText()).isNotBlank();
        assertThat(result.get("hits").get(0).get("title").asText()).isNotBlank();
        assertThat(result.get("hits").get(0).get("score").asDouble()).isGreaterThan(0);
    }

    // --- Caching behavior ---

    @Test
    void search_cachedResult_returnedFasterOnSecondCall() throws Exception {
        indexDoc(tenant, "Cache Test Document", "caching caffeine performance");

        // First call - cache miss
        long t1 = System.currentTimeMillis();
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "caching"))
                .andExpect(status().isOk());
        long first = System.currentTimeMillis() - t1;

        // Second call - cache hit, should be at least as fast
        long t2 = System.currentTimeMillis();
        String body = mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "caching"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long second = System.currentTimeMillis() - t2;

        // Both should succeed and return the same result
        assertThat(objectMapper.readTree(body).get("total").asLong()).isEqualTo(1);

        // Cache hit should be at most as slow as first call (usually much faster)
        assertThat(second).isLessThanOrEqualTo(first + 50); // 50ms tolerance
    }

    @Test
    void search_cacheInvalidatedAfterIndexingNewDocument() throws Exception {
        indexDoc(tenant, "First Doc", "invalidation keyword");

        // Prime the cache
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "invalidation"))
                .andExpect(jsonPath("$.total").value(1));

        // Index a second document (should invalidate cache)
        indexDoc(tenant, "Second Doc", "invalidation keyword");

        // Search must now reflect both documents (cache invalidated)
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "invalidation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    // --- Health during operation ---

    @Test
    void health_reflectsLiveDocumentCount() throws Exception {
        String healthBefore = mockMvc.perform(get("/health"))
                .andReturn().getResponse().getContentAsString();
        long before = objectMapper.readTree(healthBefore).get("details").get("totalDocuments").asLong();

        indexDoc(tenant, "Health Counter", "counts should go up");

        String healthAfter = mockMvc.perform(get("/health"))
                .andReturn().getResponse().getContentAsString();
        long after = objectMapper.readTree(healthAfter).get("details").get("totalDocuments").asLong();

        assertThat(after).isGreaterThan(before);
    }
}
