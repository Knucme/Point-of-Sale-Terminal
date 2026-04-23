package com.sos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT token generation, parsing, and session-cap logic.
 */
class JwtUtilTest {

    // 64-byte hex secret (matches the format used in application.properties)
    private static final String SECRET =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789" +
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private static final long EXPIRATION_MS = 600_000;        // 10 min
    private static final long MAX_SESSION_SECONDS = 43_200;   // 12 h

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS, MAX_SESSION_SECONDS);
    }

    // ── Token Generation ────────────────────────────────────────────────────

    @Test
    void generateToken_returnsNonEmptyString() {
        String token = jwtUtil.generateToken(1, "manager", "MANAGER", "Alice", nowEpoch());
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_containsExpectedClaims() {
        long loginAt = nowEpoch();
        String token = jwtUtil.generateToken(42, "boh_cook", "BOH", "Bob", loginAt);
        Claims claims = jwtUtil.parseClaims(token);

        assertEquals(42, claims.get("id", Integer.class));
        assertEquals("boh_cook", claims.get("username", String.class));
        assertEquals("BOH", claims.get("role", String.class));
        assertEquals("Bob", claims.get("name", String.class));
        assertEquals(loginAt, ((Number) claims.get("loginAt")).longValue());
    }

    // ── Token Parsing ───────────────────────────────────────────────────────

    @Test
    void parseClaims_throwsOnTamperedToken() {
        String token = jwtUtil.generateToken(1, "u", "FOH", "N", nowEpoch());
        // Flip a character in the signature portion
        String tampered = token.substring(0, token.length() - 2) + "XX";
        assertThrows(Exception.class, () -> jwtUtil.parseClaims(tampered));
    }

    @Test
    void parseClaims_throwsOnGarbageInput() {
        assertThrows(Exception.class, () -> jwtUtil.parseClaims("not.a.jwt"));
    }

    // ── Session Expiry ──────────────────────────────────────────────────────

    @Test
    void isSessionExpired_falseForFreshSession() {
        String token = jwtUtil.generateToken(1, "u", "MANAGER", "N", nowEpoch());
        Claims claims = jwtUtil.parseClaims(token);
        assertFalse(jwtUtil.isSessionExpired(claims));
    }

    @Test
    void isSessionExpired_trueForOldSession() {
        // loginAt = 50_000 seconds ago (exceeds 43_200s cap)
        long oldLoginAt = nowEpoch() - 50_000;
        String token = jwtUtil.generateToken(1, "u", "MANAGER", "N", oldLoginAt);
        Claims claims = jwtUtil.parseClaims(token);
        assertTrue(jwtUtil.isSessionExpired(claims));
    }

    @Test
    void isSessionExpired_falseWhenLoginAtMissing() {
        // Edge case: legacy token without loginAt — fail-open
        // Build a token manually without the loginAt claim (can't remove from immutable Claims)
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .claims(Map.of("id", 1, "username", "u", "role", "FOH", "name", "N"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        Claims claims = jwtUtil.parseClaims(token);
        assertFalse(jwtUtil.isSessionExpired(claims));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private long nowEpoch() {
        return System.currentTimeMillis() / 1000;
    }
}
