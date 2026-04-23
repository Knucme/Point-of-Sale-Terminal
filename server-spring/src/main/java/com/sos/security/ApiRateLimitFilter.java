package com.sos.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Per-IP rate limiter for all mutating (POST/PUT/PATCH/DELETE) API requests.
 * Protects the public demo from rapid-fire abuse.
 */
@Component
@Order(1) // run before other filters
public class ApiRateLimitFilter implements Filter {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> requests = new ConcurrentHashMap<>();

    public ApiRateLimitFilter(
            @Value("${rate-limit.api.max-requests:30}") int maxRequests,
            @Value("${rate-limit.api.window-ms:60000}") long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String method = httpReq.getMethod();
        String path = httpReq.getRequestURI();

        // Only rate-limit mutating operations on API paths
        boolean isMutating = "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
        if (!isMutating || !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String ip = httpReq.getRemoteAddr();
        if (ip == null) ip = "unknown";

        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<Long> timestamps = requests.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>());

        // Prune expired entries
        List<Long> valid = timestamps.stream()
                .filter(t -> now - t < windowMs)
                .collect(Collectors.toList());
        timestamps.clear();
        timestamps.addAll(valid);

        if (timestamps.size() >= maxRequests) {
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setStatus(429);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write(
                    "{\"error\":\"Rate limit exceeded. Maximum " + maxRequests
                            + " mutating requests per minute. Please slow down.\"}");
            return;
        }

        timestamps.add(now);
        chain.doFilter(request, response);
    }
}
