package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.lucene.queryparser.classic.ParseException;
import org.example.config.TenantContext;
import org.example.model.SearchResult;
import org.example.service.DocumentService;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Search", description = "Full-text search across tenant documents")
public class SearchController {

    private final DocumentService documentService;

    public SearchController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search documents",
            description = """
                    Full-text search across all documents for the authenticated tenant.

                    Supports Lucene query syntax:
                    - Simple: `financial report`
                    - Field-specific: `title:annual content:revenue`
                    - Boolean: `finance AND (quarterly OR annual)`
                    - Fuzzy: `finanse~`
                    - Wildcard: `report*`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results"),
            @ApiResponse(responseCode = "400", description = "Invalid query syntax or parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public SearchResult search(
            @Parameter(description = "Search query (Lucene syntax supported)", example = "financial report", required = true)
            @RequestParam String q,

            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Results per page (1–100)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) throws ParseException {
        return documentService.search(TenantContext.getTenantId(), q, page, size);
    }
}
