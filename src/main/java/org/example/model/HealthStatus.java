package org.example.model;

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
@Schema(description = "Service health status report")
public class HealthStatus {

    @Schema(description = "Overall health status", example = "UP")
    private String status;

    @Schema(description = "Per-component health details")
    private Map<String, Object> details;

    @Schema(description = "Timestamp of this health check")
    private LocalDateTime timestamp;
}
