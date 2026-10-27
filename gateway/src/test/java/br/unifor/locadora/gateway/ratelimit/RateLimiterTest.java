package br.unifor.locadora.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void bloqueiaDepoisDoLimite() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip-1", 3, Duration.ofMinutes(1)).allowed());
        }
        RateLimiter.Result r = limiter.tryAcquire("ip-1", 3, Duration.ofMinutes(1));
        assertFalse(r.allowed());
        assertTrue(r.retryAfterSeconds() > 0);
    }

    @Test
    void chavesDiferentesTemContadoresIndependentes() {
        RateLimiter limiter = new RateLimiter();
        assertTrue(limiter.tryAcquire("a", 1, Duration.ofMinutes(1)).allowed());
        assertFalse(limiter.tryAcquire("a", 1, Duration.ofMinutes(1)).allowed());
        assertTrue(limiter.tryAcquire("b", 1, Duration.ofMinutes(1)).allowed());
    }
}
