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
class HealthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void GET_health_returns200WithUpStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void GET_health_doesNotRequireXTenantIdHeader() throws Exception {
        // The health endpoint must be accessible without tenant auth
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void GET_health_includesTimestamp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void GET_health_includesAllRequiredComponentDetails() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.indexEngine").isNotEmpty())
                .andExpect(jsonPath("$.details.cache").isNotEmpty())
                .andExpect(jsonPath("$.details.rateLimiter").isNotEmpty())
                .andExpect(jsonPath("$.details.tenantsIndexed").isNumber())
                .andExpect(jsonPath("$.details.totalDocuments").isNumber());
    }

    @Test
    void GET_health_documentCountIncreasesAfterIndexing() throws Exception {
        // Capture baseline count
        String before = mockMvc.perform(get("/health"))
                .andReturn().getResponse().getContentAsString();
        long countBefore = objectMapper.readTree(before).get("details").get("totalDocuments").asLong();

        // Index a document with a unique tenant
        String tenant = "health-test-" + UUID.randomUUID();
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Health Counter Doc");
        req.setContent("should increment the total count");
        mockMvc.perform(post("/documents")
                .header("X-Tenant-ID", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        // Count should be higher now
        String after = mockMvc.perform(get("/health"))
                .andReturn().getResponse().getContentAsString();
        long countAfter = objectMapper.readTree(after).get("details").get("totalDocuments").asLong();

        org.assertj.core.api.Assertions.assertThat(countAfter).isGreaterThan(countBefore);
    }

    @Test
    void GET_health_tenantsIndexedCountIncreasesForNewTenant() throws Exception {
        String before = mockMvc.perform(get("/health"))
                .andReturn().getResponse().getContentAsString();
        long tenantsBefore = objectMapper.readTree(before).get("details").get("tenantsIndexed").asLong();

        String newTenant = "new-tenant-" + UUID.randomUUID();
        DocumentRequest req = new DocumentRequest();
        req.setTitle("New Tenant Doc");
        req.setContent("first document for this tenant");
        mockMvc.perform(post("/documents")
                .header("X-Tenant-ID", newTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        String after = mockMvc.perform(get("/health"))
                .andReturn().getResponse().getContentAsString();
        long tenantsAfter = objectMapper.readTree(after).get("details").get("tenantsIndexed").asLong();

        org.assertj.core.api.Assertions.assertThat(tenantsAfter).isGreaterThan(tenantsBefore);
    }
}
