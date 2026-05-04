package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.DocumentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private String uniqueTenant() {
        return "search-ctrl-" + UUID.randomUUID();
    }

    private void indexDoc(String tenant, String title, String content) throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle(title);
        req.setContent(content);
        mockMvc.perform(post("/documents")
                .header(TENANT_HEADER, tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    // --- Happy path ---

    @Test
    void GET_search_returns200WithMatchingHits() throws Exception {
        String tenant = uniqueTenant();
        indexDoc(tenant, "Cloud Native Guide", "kubernetes docker orchestration");

        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "kubernetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].title").value("Cloud Native Guide"))
                .andExpect(jsonPath("$.tookMs").isNumber())
                .andExpect(jsonPath("$.query").value("kubernetes"));
    }

    @Test
    void GET_search_returnsEmptyHitsForNoMatch() throws Exception {
        String tenant = uniqueTenant();
        indexDoc(tenant, "Java Basics", "object oriented programming");

        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.hits").isArray());
    }

    @Test
    void GET_search_responseIncludesAllMetadataFields() throws Exception {
        String tenant = uniqueTenant();

        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "anything")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("anything"))
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(5));
    }

    // --- Validation errors ---

    @Test
    void GET_search_returns400ForBlankQuery() throws Exception {
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, uniqueTenant())
                        .param("q", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_search_returns400WhenTenantHeaderMissing() throws Exception {
        mockMvc.perform(get("/search").param("q", "test"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_search_returns400ForPageSizeExceedingMax() throws Exception {
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, uniqueTenant())
                        .param("q", "test")
                        .param("size", "999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_search_returns400ForNegativePage() throws Exception {
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, uniqueTenant())
                        .param("q", "test")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    // --- Pagination ---

    @Test
    void GET_search_paginatesResultsCorrectly() throws Exception {
        String tenant = uniqueTenant();
        for (int i = 1; i <= 5; i++) {
            indexDoc(tenant, "Doc " + i, "paginationtestword content here");
        }

        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "paginationtestword")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.hits.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(2));
    }

    @Test
    void GET_search_secondPageReturnsDifferentResults() throws Exception {
        String tenant = uniqueTenant();
        for (int i = 1; i <= 4; i++) {
            indexDoc(tenant, "Article " + i, "pagedearchtest content");
        }

        String page0Body = mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "pagedearchtest")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String page1Body = mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "pagedearchtest")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var p0 = objectMapper.readTree(page0Body).get("hits");
        var p1 = objectMapper.readTree(page1Body).get("hits");

        // Ensure no overlap between pages
        var ids0 = new java.util.HashSet<String>();
        p0.forEach(h -> ids0.add(h.get("id").asText()));
        p1.forEach(h -> org.assertj.core.api.Assertions.assertThat(ids0).doesNotContain(h.get("id").asText()));
    }

    // --- Query syntax ---

    @Test
    void GET_search_booleanAndQueryNarrowsResults() throws Exception {
        String tenant = uniqueTenant();
        indexDoc(tenant, "Spring Boot Application", "java web framework");
        indexDoc(tenant, "Django Application", "python web framework");

        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "java AND framework"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].title").value("Spring Boot Application"));
    }

    @Test
    void GET_search_fieldSpecificQuery() throws Exception {
        String tenant = uniqueTenant();
        indexDoc(tenant, "Machine Learning Intro", "introduction to algorithms");
        indexDoc(tenant, "Deep Learning Guide", "neural networks backpropagation");

        // Only match documents whose title contains "Machine"
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "title:Machine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].title").value("Machine Learning Intro"));
    }

    // --- Multi-tenant isolation ---

    @Test
    void GET_search_tenantIsolation_resultsAreStrictlyScopedToTenant() throws Exception {
        String tenantA = uniqueTenant();
        String tenantB = uniqueTenant();
        indexDoc(tenantA, "Tenant A Exclusive Doc", "isolationtestterm");
        indexDoc(tenantB, "Tenant B Exclusive Doc", "isolationtestterm");

        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenantA)
                        .param("q", "isolationtestterm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].title").value("Tenant A Exclusive Doc"));
    }
}
