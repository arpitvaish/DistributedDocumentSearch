package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.config.TenantContext;
import org.example.model.Document;
import org.example.model.DocumentRequest;
import org.example.service.DocumentParserService;
import org.example.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
@Tag(name = "Documents", description = "Document indexing and retrieval")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentParserService documentParserService;

    public DocumentController(DocumentService documentService, DocumentParserService documentParserService) {
        this.documentService = documentService;
        this.documentParserService = documentParserService;
    }

    @PostMapping
    @Operation(
            summary = "Index a new document",
            description = "Adds a document to the full-text search index for the authenticated tenant."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document indexed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Document> indexDocument(@Valid @RequestBody DocumentRequest request) {
        Document doc = documentService.indexDocument(TenantContext.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Retrieve a document by ID",
            description = "Fetches a document belonging to the authenticated tenant."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Document> getDocument(
            @Parameter(description = "Document ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String id) {
        return ResponseEntity.ok(documentService.getDocument(TenantContext.getTenantId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a document",
            description = "Removes a document from the search index. Only the owning tenant can delete."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted"),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String id) {
        documentService.deleteDocument(TenantContext.getTenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload and index a file",
            description = "Accepts a PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, HTML, RTF, or ODT file. " +
                    "Text is extracted via Apache Tika and indexed for full-text search."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File parsed and document indexed"),
            @ApiResponse(responseCode = "400", description = "Empty file or no extractable text"),
            @ApiResponse(responseCode = "413", description = "File exceeds 10 MB size limit"),
            @ApiResponse(responseCode = "415", description = "Unsupported file type"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Document> uploadDocument(
            @Parameter(description = "File to parse and index (max 10 MB)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional title override; defaults to the filename without extension")
            @RequestParam(value = "title", required = false) String title) throws java.io.IOException {

        DocumentParserService.ParsedDocument parsed = documentParserService.parse(file);

        DocumentRequest request = new DocumentRequest();
        request.setTitle(title != null && !title.isBlank() ? title : parsed.titleFromFilename());
        request.setContent(parsed.text());

        Document doc = documentService.indexDocument(TenantContext.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }
}
