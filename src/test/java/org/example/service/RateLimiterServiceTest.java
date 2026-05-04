package org.example.service;

import org.example.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RateLimiterServiceTest {

    @Test
    void checkRateLimit_firstRequest_isAllowed() {
        var service = new RateLimiterService(100.0);
        assertThatNoException().isThrownBy(() -> service.checkRateLimit("tenant-a"));
    }

    @Test
    void checkRateLimit_requestsSpacedByRateInterval_allSucceed() throws InterruptedException {
        // 10 RPS means one token every 100ms; calls spaced >100ms apart must always succeed
        var service = new RateLimiterService(10.0);
        service.checkRateLimit("tenant-a"); // first token — immediate
        Thread.sleep(110);                  // wait one interval + margin
        assertThatNoException().isThrownBy(() -> service.checkRateLimit("tenant-a"));
    }

    @Test
    void checkRateLimit_exceedingLimit_throwsRateLimitException() {
        // Near-zero rate ensures the limiter runs dry after the first token
        var service = new RateLimiterService(0.001);
        assertThatThrownBy(() -> {
            for (int i = 0; i < 100; i++) service.checkRateLimit("tenant-a");
        })
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("tenant-a");
    }

    @Test
    void checkRateLimit_differentTenantsHaveIndependentLimiters() {
        var service = new RateLimiterService(0.001); // near-zero

        // Exhaust tenant-a's limiter
        try {
            for (int i = 0; i < 100; i++) service.checkRateLimit("tenant-a");
        } catch (RateLimitExceededException ignored) {}

        // tenant-b should still have a fresh, unaffected limiter
        assertThatNoException().isThrownBy(() -> service.checkRateLimit("tenant-b"));
    }

    @Test
    void checkRateLimit_exceptionMessageContainsTenantId() {
        var service = new RateLimiterService(0.001);
        String tenantId = "my-specific-tenant";

        assertThatThrownBy(() -> {
            for (int i = 0; i < 100; i++) service.checkRateLimit(tenantId);
        })
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining(tenantId);
    }

    @Test
    void getStats_returnsConfiguredRateForActiveTenants() {
        var service = new RateLimiterService(42.0);
        service.checkRateLimit("tenant-x");
        service.checkRateLimit("tenant-y");

        var stats = service.getStats();
        assertThat(stats).containsKeys("tenant-x", "tenant-y");
        assertThat(stats.get("tenant-x")).isEqualTo(42.0);
        assertThat(stats.get("tenant-y")).isEqualTo(42.0);
    }

    @Test
    void getStats_returnsEmptyMapWhenNoRequestsMade() {
        var service = new RateLimiterService(100.0);
        assertThat(service.getStats()).isEmpty();
    }
}
