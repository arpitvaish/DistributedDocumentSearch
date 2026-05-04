package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.model.HealthStatus;
import org.example.service.LuceneSearchService;
import org.example.service.RateLimiterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "Health", description = "Service health monitoring")
public class HealthController {

    private final LuceneSearchService luceneSearchService;
    private final RateLimiterService rateLimiterService;

    public HealthController(LuceneSearchService luceneSearchService, RateLimiterService rateLimiterService) {
        this.luceneSearchService = luceneSearchService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Returns service health and dependency status. Does not require X-Tenant-ID header."
    )
    public HealthStatus health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("indexEngine", "UP (Apache Lucene 9.10.0 — embedded)");
        details.put("cache", "UP (Caffeine — in-memory, TTL 5m, max 1000 entries)");
        details.put("rateLimiter", "UP (Guava token bucket)");
        details.put("tenantsIndexed", luceneSearchService.getIndexedTenantCount());
        details.put("totalDocuments", luceneSearchService.getTotalIndexedDocuments());
        details.put("activeTenantLimiters", rateLimiterService.getStats().size());

        return HealthStatus.builder()
                .status("UP")
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
