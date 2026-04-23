package com.sos.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the login rate-limiter.
 */
class LoginRateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 300_000);
        for (int i = 0; i < 5; i++) {
            assertFalse(limiter.isLimited("10.0.0.1"),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void blocksAfterExceedingLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 300_000);
        // Use up the 3 allowed attempts
        for (int i = 0; i < 3; i++) {
            limiter.isLimited("10.0.0.2");
        }
        assertTrue(limiter.isLimited("10.0.0.2"),
                "4th attempt should be blocked");
    }

    @Test
    void differentIpsHaveSeparateBuckets() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 300_000);
        limiter.isLimited("A");
        limiter.isLimited("A");
        // A is now at limit, but B should still be free
        assertFalse(limiter.isLimited("B"));
        assertTrue(limiter.isLimited("A"));
    }

    @Test
    void handlesNullIpGracefully() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 300_000);
        assertFalse(limiter.isLimited(null));
    }
}
