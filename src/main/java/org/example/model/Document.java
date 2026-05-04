package org.example.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Indexed document entity")
public class Document {

    @Schema(description = "Unique document ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Tenant that owns this document", example = "tenant-a")
    private String tenantId;

    @Schema(description = "Document title", example = "Annual Report 2024")
    private String title;

    @Schema(description = "Full document content")
    private String content;

    @Schema(description = "Additional metadata key-value pairs")
    private Map<String, String> metadata;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
