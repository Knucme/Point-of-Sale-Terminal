package com.sos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowMs;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${rate-limit.login.max-attempts:10}") int maxAttempts,
            @Value("${rate-limit.login.window-ms:300000}") long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    public boolean isLimited(String ip) {
        String key = ip != null ? ip : "unknown";
        long now = System.currentTimeMillis();

        CopyOnWriteArrayList<Long> list = attempts.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());

        List<Long> valid = list.stream()
                .filter(t -> now - t < windowMs)
                .collect(Collectors.toList());
        list.clear();
        list.addAll(valid);

        if (list.size() >= maxAttempts) {
            return true;
        }

        list.add(now);
        return false;
    }
}
