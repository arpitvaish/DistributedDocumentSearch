package org.example.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search response containing matched documents")
public class SearchResult {

    @Schema(description = "List of matching document hits")
    private List<Hit> hits;

    @Schema(description = "Total number of matching documents", example = "42")
    private long total;

    @Schema(description = "Original search query", example = "financial report")
    private String query;

    @Schema(description = "Tenant ID for this result set", example = "tenant-a")
    private String tenantId;

    @Schema(description = "Query execution time in milliseconds", example = "23")
    private long tookMs;

    @Schema(description = "Current page (0-indexed)", example = "0")
    private int page;

    @Schema(description = "Page size", example = "10")
    private int pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Individual search hit")
    public static class Hit {

        @Schema(description = "Document ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String id;

        @Schema(description = "Document title", example = "Annual Report 2024")
        private String title;

        @Schema(description = "Content snippet with relevant context", example = "...highlights from the financial report...")
        private String contentSnippet;

        @Schema(description = "Relevance score", example = "1.2345")
        private float score;

        @Schema(description = "Document creation timestamp", example = "2024-01-15T10:30:00")
        private String createdAt;

        @Schema(description = "Document metadata")
        private Map<String, String> metadata;
    }
}
