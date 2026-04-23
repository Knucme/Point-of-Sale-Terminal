package com.sos.controller;

import com.sos.dto.*;
import com.sos.model.Role;
import com.sos.model.User;
import com.sos.model.UserStatus;
import com.sos.repository.SecurityLogRepository;
import com.sos.repository.UserRepository;
import com.sos.security.JwtPrincipal;
import com.sos.service.SecurityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// User management endpoints + security logs
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final int PASSWORD_MIN_LENGTH = 12;
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final List<String> VALID_ROLES = List.of("BOH", "FOH", "MANAGER");

    private final UserRepository userRepo;
    private final SecurityLogRepository logRepo;
    private final SecurityLogService securityLog;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepo,
                          SecurityLogRepository logRepo,
                          SecurityLogService securityLog,
                          PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.logRepo = logRepo;
        this.securityLog = securityLog;
        this.passwordEncoder = passwordEncoder;
    }

    private String validatePassword(String pw) {
        if (pw == null || pw.length() < PASSWORD_MIN_LENGTH) {
            return "Password must be at least " + PASSWORD_MIN_LENGTH + " characters.";
        }
        if (!HAS_LETTER.matcher(pw).find() || !HAS_DIGIT.matcher(pw).find()) {
            return "Password must contain at least one letter and one digit.";
        }
        return null;
    }

    // ── GET /api/users ──────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getUsers() {
        try {
            List<User> users = userRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            // Return without passwordHash (already @JsonIgnore) — matches Node select shape
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch users."));
        }
    }

    // ── GET /api/users/security-logs ────────────────────────────────────────
    // Manager views the security log. Defined before /:id to avoid conflict.
    @GetMapping("/security-logs")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getSecurityLogs(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            int cappedLimit = Math.min(limit, 500);
            var pageable = org.springframework.data.domain.PageRequest.of(
                    offset / Math.max(cappedLimit, 1), cappedLimit,
                    Sort.by(Sort.Direction.DESC, "timestamp"));

            var page = logRepo.findAll(pageable);

            // Match Node.js response shape: { logs, total, limit, offset }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("logs", page.getContent());
            result.put("total", page.getTotalElements());
            result.put("limit", cappedLimit);
            result.put("offset", offset);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch security logs."));
        }
    }

    // ── GET /api/users/:id ──────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getUserById(@PathVariable int id) {
        try {
            User user = userRepo.findById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("User not found."));
            }
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch user."));
        }
    }

    // ── POST /api/users ─────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest req,
                                        @AuthenticationPrincipal JwtPrincipal principal,
                                        HttpServletRequest httpReq) {
        if (!VALID_ROLES.contains(req.getRole())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("role must be one of: " + String.join(", ", VALID_ROLES) + "."));
        }
        String pwError = validatePassword(req.getPassword());
        if (pwError != null) {
            return ResponseEntity.badRequest().body(new ErrorResponse(pwError));
        }

        try {
            var existing = userRepo.findByUsername(req.getUsername());
            if (existing.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse("That username is already taken. Please choose another."));
            }

            User user = new User();
            user.setName(req.getName().trim());
            user.setUsername(req.getUsername().trim());
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            user.setRole(Role.valueOf(req.getRole()));

            user = userRepo.save(user);

            securityLog.logEvent(principal.getId(), "USER_CREATED",
                    "Manager created account: " + user.getUsername() + " (" + user.getRole() + ")",
                    httpReq.getRemoteAddr());

            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create user account."));
        }
    }

    // ── PATCH /api/users/:id ────────────────────────────────────────────────
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable int id,
                                        @RequestBody UpdateUserRequest req,
                                        @AuthenticationPrincipal JwtPrincipal principal,
                                        HttpServletRequest httpReq) {
        if (req.getStatus() != null && !"ACTIVE".equals(req.getStatus()) && !"INACTIVE".equals(req.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("status must be ACTIVE or INACTIVE."));
        }
        if (req.getRole() != null && !VALID_ROLES.contains(req.getRole())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("role must be one of: " + String.join(", ", VALID_ROLES) + "."));
        }
        if (req.getPassword() != null) {
            String pwError = validatePassword(req.getPassword());
            if (pwError != null) {
                return ResponseEntity.badRequest().body(new ErrorResponse(pwError));
            }
        }

        try {
            User target = userRepo.findById(id).orElse(null);
            if (target == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("User not found."));
            }

            // Self-protection
            if (id == principal.getId()) {
                if (req.getRole() != null && !"MANAGER".equals(req.getRole())) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("You cannot change your own role."));
                }
                if ("INACTIVE".equals(req.getStatus())) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("You cannot deactivate your own account."));
                }
            }

            // Peer-manager protection
            if (target.getRole() == Role.MANAGER && target.getId() != principal.getId()) {
                if (req.getRole() != null && !"MANAGER".equals(req.getRole())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(new ErrorResponse("Managers cannot demote other managers."));
                }
                if ("INACTIVE".equals(req.getStatus())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(new ErrorResponse("Managers cannot deactivate other managers."));
                }
            }

            List<String> changed = new ArrayList<>();
            if (req.getStatus() != null) {
                target.setStatus(UserStatus.valueOf(req.getStatus()));
                changed.add("status=" + req.getStatus());
            }
            if (req.getRole() != null) {
                target.setRole(Role.valueOf(req.getRole()));
                changed.add("role=" + req.getRole());
            }
            if (req.getName() != null) {
                target.setName(req.getName().trim());
                changed.add("name updated");
            }
            if (req.getPassword() != null) {
                target.setPasswordHash(passwordEncoder.encode(req.getPassword()));
                changed.add("password reset");
            }

            target = userRepo.save(target);

            securityLog.logEvent(principal.getId(),
                    req.getPassword() != null ? "PASSWORD_RESET" : "PERMISSION_CHANGE",
                    "Manager updated user " + id + ": " + (changed.isEmpty() ? "no-op" : String.join(", ", changed)),
                    httpReq.getRemoteAddr());

            return ResponseEntity.ok(target);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update user."));
        }
    }

    // ── DELETE /api/users/:id ────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable int id,
                                        @AuthenticationPrincipal JwtPrincipal principal,
                                        HttpServletRequest httpReq) {
        // Prevent self-deletion
        if (id == principal.getId()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("You cannot delete your own account."));
        }

        try {
            User target = userRepo.findById(id).orElse(null);
            if (target == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("User not found."));
            }
            if (target.getRole() == Role.MANAGER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Managers cannot delete other managers."));
            }

            userRepo.delete(target);

            securityLog.logEvent(principal.getId(), "USER_DELETED",
                    "Manager deleted user " + id,
                    httpReq.getRemoteAddr());

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete user."));
        }
    }
}
