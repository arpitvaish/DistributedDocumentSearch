package org.example.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String tenantId) {
        super("Rate limit exceeded for tenant: " + tenantId);
    }
}
