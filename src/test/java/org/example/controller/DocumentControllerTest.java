package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Document;
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
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private String uniqueTenant() {
        return "doc-ctrl-" + UUID.randomUUID();
    }

    private String indexDoc(String tenant, String title, String content) throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle(title);
        req.setContent(content);

        String body = mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, Document.class).getId();
    }

    // --- POST /documents ---

    @Test
    void POST_documents_returns201WithDocumentBody() throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Integration Guide");
        req.setContent("Step by step instructions");

        mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, uniqueTenant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Integration Guide"))
                .andExpect(jsonPath("$.tenantId").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void POST_documents_storesMetadataWhenProvided() throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Report");
        req.setContent("Content");
        req.setMetadata(java.util.Map.of("author", "Alice", "category", "finance"));

        mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, uniqueTenant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.author").value("Alice"))
                .andExpect(jsonPath("$.metadata.category").value("finance"));
    }

    @Test
    void POST_documents_returns400WhenTitleIsMissing() throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setContent("Content without a title");

        mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, uniqueTenant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_documents_returns400WhenContentIsMissing() throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Title without content");

        mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, uniqueTenant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_documents_returns400WhenTenantHeaderMissing() throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Title");
        req.setContent("Content");

        mockMvc.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_documents_returns400WhenTenantHeaderIsBlank() throws Exception {
        DocumentRequest req = new DocumentRequest();
        req.setTitle("Title");
        req.setContent("Content");

        mockMvc.perform(post("/documents")
                        .header(TENANT_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // --- GET /documents/{id} ---

    @Test
    void GET_documentsById_returns200ForExistingDocument() throws Exception {
        String tenant = uniqueTenant();
        String id = indexDoc(tenant, "Retrievable Document", "some content");

        mockMvc.perform(get("/documents/" + id).header(TENANT_HEADER, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Retrievable Document"));
    }

    @Test
    void GET_documentsById_returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/documents/totally-unknown-id").header(TENANT_HEADER, uniqueTenant()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void GET_documentsById_returns400WhenTenantHeaderMissing() throws Exception {
        mockMvc.perform(get("/documents/any-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_documentsById_tenantIsolation_returns404ForOtherTenantDoc() throws Exception {
        String tenantA = uniqueTenant();
        String id = indexDoc(tenantA, "Private Document", "sensitive content");

        // Different tenant cannot access tenantA's document
        mockMvc.perform(get("/documents/" + id).header(TENANT_HEADER, uniqueTenant()))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /documents/{id} ---

    @Test
    void DELETE_documents_returns204AndMakesDocumentUnavailable() throws Exception {
        String tenant = uniqueTenant();
        String id = indexDoc(tenant, "To Delete", "content");

        mockMvc.perform(delete("/documents/" + id).header(TENANT_HEADER, tenant))
                .andExpect(status().isNoContent());

        // Subsequent GET must 404
        mockMvc.perform(get("/documents/" + id).header(TENANT_HEADER, tenant))
                .andExpect(status().isNotFound());
    }

    @Test
    void DELETE_documents_returns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/documents/ghost-id").header(TENANT_HEADER, uniqueTenant()))
                .andExpect(status().isNotFound());
    }

    @Test
    void DELETE_documents_returns400WhenTenantHeaderMissing() throws Exception {
        mockMvc.perform(delete("/documents/any-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void DELETE_documents_tenantIsolation_cannotDeleteOtherTenantDoc() throws Exception {
        String tenantA = uniqueTenant();
        String id = indexDoc(tenantA, "Protected Doc", "content");

        // Another tenant trying to delete tenantA's doc gets 404
        mockMvc.perform(delete("/documents/" + id).header(TENANT_HEADER, uniqueTenant()))
                .andExpect(status().isNotFound());

        // Original document is still accessible by tenantA
        mockMvc.perform(get("/documents/" + id).header(TENANT_HEADER, tenantA))
                .andExpect(status().isOk());
    }
}
