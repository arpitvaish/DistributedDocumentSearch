package org.example.service;

import com.google.common.util.concurrent.RateLimiter;
import org.example.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RateLimiterService {

    private final double requestsPerSecond;
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimiterService(@Value("${app.rate-limit.requests-per-second:100.0}") double requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public void checkRateLimit(String tenantId) {
        RateLimiter limiter = limiters.computeIfAbsent(tenantId, k -> RateLimiter.create(requestsPerSecond));
        if (!limiter.tryAcquire()) {
            throw new RateLimitExceededException(tenantId);
        }
    }

    public Map<String, Double> getStats() {
        return limiters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getRate()));
    }
}
