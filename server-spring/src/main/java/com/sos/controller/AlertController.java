package com.sos.controller;

import com.sos.dto.CreateAlertRequest;
import com.sos.dto.ErrorResponse;
import com.sos.model.Alert;
import com.sos.model.RecipientScope;
import com.sos.model.User;
import com.sos.repository.AlertRepository;
import com.sos.repository.UserRepository;
import com.sos.security.JwtPrincipal;
import com.sos.service.SocketIOService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Alert endpoints: create, list, and mark as read
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertRepository alertRepo;
    private final UserRepository userRepo;
    private final SocketIOService socketIO;

    public AlertController(AlertRepository alertRepo, UserRepository userRepo, SocketIOService socketIO) {
        this.alertRepo = alertRepo;
        this.userRepo = userRepo;
        this.socketIO = socketIO;
    }

    // ── POST /api/alerts ────────────────────────────────────────────────────
    // BOH sends an alert (broadcast to all FOH, or targeted to a specific terminal).
    @PostMapping
    @PreAuthorize("hasRole('BOH')")
    @Transactional
    public ResponseEntity<?> createAlert(@Valid @RequestBody CreateAlertRequest req,
                                         @AuthenticationPrincipal JwtPrincipal principal) {
        String scope = req.getRecipientScope();
        if (!"BROADCAST".equals(scope) && !"SPECIFIC".equals(scope)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("recipientScope must be BROADCAST or SPECIFIC."));
        }
        if ("SPECIFIC".equals(scope) && req.getTargetUserId() == null) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("targetUserId is required when recipientScope is SPECIFIC."));
        }

        try {
            User sender = userRepo.findById(principal.getId()).orElseThrow();

            Alert alert = new Alert();
            alert.setSender(sender);
            alert.setRecipientScope(RecipientScope.valueOf(scope));
            alert.setMessage(req.getMessage().trim());

            alert = alertRepo.save(alert);

            // Re-fetch to include sender relationship for JSON response
            alert = alertRepo.findById(alert.getId()).orElse(alert);

            // ── Socket: alert:broadcast → FOH (or specific user) ────────────
            Map<String, Object> alertPayload = new LinkedHashMap<>();
            alertPayload.put("id", alert.getId());
            alertPayload.put("senderId", alert.getSenderId());
            alertPayload.put("recipientScope", alert.getRecipientScope().name());
            alertPayload.put("message", alert.getMessage());
            alertPayload.put("timestamp", alert.getTimestamp() != null ? alert.getTimestamp().toString() : null);
            alertPayload.put("isRead", alert.getIsRead());
            if (alert.getSender() != null) {
                alertPayload.put("sender", Map.of(
                        "id", alert.getSender().getId(),
                        "name", alert.getSender().getName()
                ));
            }

            if ("BROADCAST".equals(scope)) {
                log.info("Emitting alert:broadcast to FOH room, payload id={}", alertPayload.get("id"));
                socketIO.emitToRoom("FOH", "alert:broadcast", alertPayload);
            } else {
                // SPECIFIC — emit to the targeted user's personal room
                socketIO.emitToRoom("user:" + req.getTargetUserId(), "alert:broadcast", alertPayload);
            }
            // MANAGER always receives alerts (matches Node.js behavior)
            log.info("Emitting alert:broadcast to MANAGER room, payload id={}", alertPayload.get("id"));
            socketIO.emitToRoom("MANAGER", "alert:broadcast", alertPayload);

            return ResponseEntity.status(HttpStatus.CREATED).body(alert);
        } catch (Exception e) {
            log.error("Failed to send alert: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send alert."));
        }
    }

    // ── GET /api/alerts ─────────────────────────────────────────────────────
    // BOH and MANAGER can view sent alerts.
    @GetMapping
    @PreAuthorize("hasAnyRole('BOH', 'MANAGER')")
    public ResponseEntity<?> getAlerts() {
        try {
            List<Alert> alerts = alertRepo.findAll(
                    PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp"))
            ).getContent();
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch alerts."));
        }
    }

    // ── PATCH /api/alerts/:id/read ──────────────────────────────────────────
    // Sender (BOH) or MANAGER may mark alerts as read.
    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markAsRead(@PathVariable int id,
                                        @AuthenticationPrincipal JwtPrincipal principal) {
        try {
            Alert alert = alertRepo.findById(id).orElse(null);
            if (alert == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Alert not found."));
            }

            boolean isSender = alert.getSenderId() != null && alert.getSenderId().equals(principal.getId());
            boolean isManager = "MANAGER".equals(principal.getRole());
            if (!isSender && !isManager) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("You do not have permission to modify this alert."));
            }

            alert.setIsRead(true);
            alert = alertRepo.save(alert);
            return ResponseEntity.ok(alert);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to mark alert as read."));
        }
    }
}
