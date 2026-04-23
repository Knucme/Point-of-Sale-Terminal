package com.sos.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

// Handles creating and validating JWT tokens.
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;
    private final long maxSessionSeconds;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.max-session-seconds}") long maxSessionSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.maxSessionSeconds = maxSessionSeconds;
    }

    // Generate a new token
    public String generateToken(Integer id, String username, String role, String name, long loginAt) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(Map.of(
                        "id", id,
                        "username", username,
                        "role", role,
                        "name", name,
                        "loginAt", loginAt
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // Parse and validate a token
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Check if the session is past the 12h cap
    public boolean isSessionExpired(Claims claims) {
        Object loginAtObj = claims.get("loginAt");
        if (loginAtObj == null) return false; // fail-open for legacy tokens
        long loginAt = ((Number) loginAtObj).longValue();
        long ageSeconds = (System.currentTimeMillis() / 1000) - loginAt;
        return ageSeconds > maxSessionSeconds;
    }

    public long getMaxSessionSeconds() {
        return maxSessionSeconds;
    }
}
