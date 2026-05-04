package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end demo: upload real-ish files, then exercise the search pipeline
 * across plain text, HTML, and multi-tenant scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileUploadSearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private String uniqueTenant() {
        return "upload-demo-" + UUID.randomUUID();
    }

    // ── Plain text upload ────────────────────────────────────────────────────

    @Test
    void uploadPlainText_titleDerivedFromFilename_contentSearchable() throws Exception {
        String tenant = uniqueTenant();
        String content = "Apache Kafka is a distributed event streaming platform. "
                + "It is used for high-performance data pipelines and streaming analytics.";

        MockMultipartFile file = new MockMultipartFile(
                "file", "kafka_overview.txt", "text/plain", content.getBytes());

        // Upload
        String body = mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .header(TENANT_HEADER, tenant))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("kafka overview"))   // underscores → spaces
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readValue(body, Document.class).getId();

        // The indexed document is retrievable by ID
        mockMvc.perform(get("/documents/" + docId).header(TENANT_HEADER, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId));

        // Full-text search finds the document by a term inside the file
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "streaming analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].id").value(docId))
                .andExpect(jsonPath("$.hits[0].title").value("kafka overview"));
    }

    // ── HTML upload ──────────────────────────────────────────────────────────

    @Test
    void uploadHtml_tagsStripped_textSearchable() throws Exception {
        String tenant = uniqueTenant();
        String html = "<html><head><title>Spring Boot Guide</title></head><body>"
                + "<h1>Getting Started with Spring Boot</h1>"
                + "<p>Spring Boot simplifies dependency injection and auto-configuration "
                + "for microservices built on the JVM.</p>"
                + "</body></html>";

        MockMultipartFile file = new MockMultipartFile(
                "file", "spring_boot_guide.html", "text/html", html.getBytes());

        String body = mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .header(TENANT_HEADER, tenant))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("spring boot guide"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readValue(body, Document.class).getId();

        // HTML tags must not leak into the search index
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "dependency injection microservices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].id").value(docId));

        // Verify HTML tags aren't surfaced in the stored content snippet
        String searchBody = mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "spring"))
                .andReturn().getResponse().getContentAsString();

        String snippet = objectMapper.readTree(searchBody)
                .get("hits").get(0).get("contentSnippet").asText();
        assertThat(snippet).doesNotContain("<h1>").doesNotContain("<p>").doesNotContain("</html>");
    }

    // ── Custom title override ────────────────────────────────────────────────

    @Test
    void uploadWithTitleOverride_customTitleUsedInsteadOfFilename() throws Exception {
        String tenant = uniqueTenant();
        MockMultipartFile file = new MockMultipartFile(
                "file", "raw_export_2024_q4.txt", "text/plain",
                "Revenue grew 23 percent year over year in Q4 2024.".getBytes());

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("title", "Q4 2024 Financial Summary")
                        .header(TENANT_HEADER, tenant))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Q4 2024 Financial Summary"));

        // Search by both custom title words and content words
        mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "financial revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].title").value("Q4 2024 Financial Summary"));
    }

    // ── Multiple uploads → ranked search ────────────────────────────────────

    @Test
    void multipleUploads_searchRanksHigherTermFrequencyFirst() throws Exception {
        String tenant = uniqueTenant();

        MockMultipartFile doc1 = new MockMultipartFile(
                "file", "kubernetes_intro.txt", "text/plain",
                "Kubernetes is a container orchestration platform.".getBytes());

        MockMultipartFile doc2 = new MockMultipartFile(
                "file", "kubernetes_deep_dive.txt", "text/plain",
                ("Kubernetes kubernetes kubernetes — container orchestration with kubernetes. "
                 + "Deploy kubernetes workloads using kubernetes controllers.").getBytes());

        mockMvc.perform(multipart("/documents/upload").file(doc1).header(TENANT_HEADER, tenant))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/documents/upload").file(doc2).header(TENANT_HEADER, tenant))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/search")
                        .header(TENANT_HEADER, tenant)
                        .param("q", "kubernetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn().getResponse().getContentAsString();

        // Document with higher term frequency must rank first
        var hits = objectMapper.readTree(body).get("hits");
        double score0 = hits.get(0).get("score").asDouble();
        double score1 = hits.get(1).get("score").asDouble();
        assertThat(score0).isGreaterThan(score1);
        assertThat(hits.get(0).get("title").asText()).isEqualTo("kubernetes deep dive");
    }

    // ── Upload then delete, verify gone ─────────────────────────────────────

    @Test
    void uploadThenDelete_documentNoLongerSearchable() throws Exception {
        String tenant = uniqueTenant();
        MockMultipartFile file = new MockMultipartFile(
                "file", "ephemeral_report.txt", "text/plain",
                "This report will be deleted shortly after upload.".getBytes());

        String body = mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .header(TENANT_HEADER, tenant))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readValue(body, Document.class).getId();

        // Confirm it's searchable before deletion
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "ephemeral"))
                .andExpect(jsonPath("$.total").value(1));

        // Delete it
        mockMvc.perform(delete("/documents/" + docId).header(TENANT_HEADER, tenant))
                .andExpect(status().isNoContent());

        // No longer searchable
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenant).param("q", "ephemeral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // No longer retrievable by ID
        mockMvc.perform(get("/documents/" + docId).header(TENANT_HEADER, tenant))
                .andExpect(status().isNotFound());
    }

    // ── Tenant isolation for uploads ─────────────────────────────────────────

    @Test
    void upload_tenantIsolation_otherTenantCannotSeeUploadedDocument() throws Exception {
        String tenantA = uniqueTenant();
        String tenantB = uniqueTenant();

        MockMultipartFile file = new MockMultipartFile(
                "file", "confidential_roadmap.txt", "text/plain",
                "Secret product roadmap: launching AI-powered search in Q3.".getBytes());

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .header(TENANT_HEADER, tenantA))
                .andExpect(status().isCreated());

        // Tenant A can find the document
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenantA).param("q", "roadmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // Tenant B sees nothing
        mockMvc.perform(get("/search").header(TENANT_HEADER, tenantB).param("q", "roadmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // ── Error cases ──────────────────────────────────────────────────────────

    @Test
    void uploadEmptyFile_returns400() throws Exception {
        mockMvc.perform(multipart("/documents/upload")
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                        .header(TENANT_HEADER, uniqueTenant()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void uploadUnsupportedType_returns415() throws Exception {
        mockMvc.perform(multipart("/documents/upload")
                        .file(new MockMultipartFile("file", "archive.zip", "application/zip",
                                new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00}))
                        .header(TENANT_HEADER, uniqueTenant()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void upload_missingTenantHeader_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/documents/upload").file(file))
                .andExpect(status().isBadRequest());
    }
}
