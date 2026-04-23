package com.sos.security;

/**
 * Lightweight object stored in the SecurityContext after JWT validation.
 * Mirrors the claims in the Node.js JWT: id, username, role, name, loginAt.
 */
public class JwtPrincipal {
    private final int id;
    private final String username;
    private final String role;
    private final String name;
    private final long loginAt;

    public JwtPrincipal(int id, String username, String role, String name, long loginAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.name = name;
        this.loginAt = loginAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public long getLoginAt() { return loginAt; }
}
