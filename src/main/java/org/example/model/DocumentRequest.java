package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Request body for indexing a document")
public class DocumentRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title cannot exceed 500 characters")
    @Schema(description = "Document title", example = "Annual Report 2024", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "Content is required")
    @Schema(description = "Full document content", example = "This report covers financial performance...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "Optional metadata", example = "{\"author\": \"Jane Doe\", \"category\": \"finance\"}")
    private Map<String, String> metadata;
}
