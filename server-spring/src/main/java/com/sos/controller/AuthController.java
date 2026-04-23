package com.sos.controller;

import com.sos.dto.*;
import com.sos.model.User;
import com.sos.model.UserStatus;
import com.sos.repository.UserRepository;
import com.sos.security.JwtPrincipal;
import com.sos.security.JwtUtil;
import com.sos.security.LoginRateLimiter;
import com.sos.service.SecurityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

// Auth endpoints: login, refresh, and me
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Dummy BCrypt hash so we always run compare (prevents timing-based enumeration)
    private static final String DUMMY_HASH =
            "$2b$12$CwTycUXWue0Thq9StjUM0uJ8yS2H5kZg.i1s5H0u4/l1y4Yh/Oy1m";

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;
    private final SecurityLogService securityLog;

    public AuthController(UserRepository userRepo,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          LoginRateLimiter rateLimiter,
                          SecurityLogService securityLog) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
        this.securityLog = securityLog;
    }

    // ── POST /api/auth/login ───────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest httpReq) {

        String ip = httpReq.getRemoteAddr();

        // Rate limit check
        if (rateLimiter.isLimited(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("Too many login attempts. Please try again shortly."));
        }

        User user = userRepo.findByUsername(req.getUsername()).orElse(null);

        // Always run BCrypt compare to prevent timing-based username enumeration
        String hashToCheck = (user != null && user.getStatus() == UserStatus.ACTIVE)
                ? user.getPasswordHash()
                : DUMMY_HASH;
        boolean passwordMatch = passwordEncoder.matches(req.getPassword(), hashToCheck);

        // Unknown user
        if (user == null) {
            try { securityLog.logEvent(null, "LOGIN_FAILURE",
                    "Unknown username: " + req.getUsername(), ip); } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password."));
        }

        // UC-11 Ext 1a: Account inactive
        if (user.getStatus() == UserStatus.INACTIVE) {
            try { securityLog.logEvent(user.getId(), "LOGIN_FAILURE", "Account inactive", ip); } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Account is not active. Please contact your manager."));
        }

        // Wrong password
        if (!passwordMatch) {
            try { securityLog.logEvent(user.getId(), "LOGIN_FAILURE", "Incorrect password", ip); } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password."));
        }

        // Success — generate JWT with loginAt for absolute session cap
        long loginAt = System.currentTimeMillis() / 1000;
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getName(), loginAt);

        try { securityLog.logEvent(user.getId(), "LOGIN_SUCCESS", null, ip); } catch (Exception ignored) {}

        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(), user.getName(), user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(new LoginResponse(token, info));
    }

    // ── POST /api/auth/refresh ─────────────────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@AuthenticationPrincipal JwtPrincipal principal,
                                     HttpServletRequest httpReq) {

        // Absolute session cap check
        long originLoginAt = principal.getLoginAt() > 0
                ? principal.getLoginAt()
                : System.currentTimeMillis() / 1000;

        long ageSeconds = (System.currentTimeMillis() / 1000) - originLoginAt;
        if (ageSeconds > jwtUtil.getMaxSessionSeconds()) {
            securityLog.logEvent(principal.getId(), "SESSION_MAX_AGE",
                    String.format("Refresh denied: session %ds exceeds max %ds",
                            ageSeconds, jwtUtil.getMaxSessionSeconds()),
                    httpReq.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Session expired. Please log in again."));
        }

        String token = jwtUtil.generateToken(
                principal.getId(), principal.getUsername(),
                principal.getRole(), principal.getName(), originLoginAt);

        return ResponseEntity.ok(new TokenResponse(token));
    }

    // ── GET /api/auth/me ───────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal JwtPrincipal principal) {
        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                principal.getId(), principal.getName(),
                principal.getUsername(), principal.getRole());
        return ResponseEntity.ok(java.util.Map.of("user", info));
    }
}
