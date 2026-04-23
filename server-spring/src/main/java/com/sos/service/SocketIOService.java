package com.sos.service;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sos.model.Alert;
import com.sos.repository.AlertRepository;
import com.sos.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// Manages Socket.IO connections — handles auth, room assignment, missed alerts, and real-time events.
@Service
public class SocketIOService {

    private static final Logger log = LoggerFactory.getLogger(SocketIOService.class);

    private final SocketIOServer server;
    private final JwtUtil jwtUtil;
    private final AlertRepository alertRepo;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SocketIOService(SocketIOServer server, JwtUtil jwtUtil, AlertRepository alertRepo) {
        this.server = server;
        this.jwtUtil = jwtUtil;
        this.alertRepo = alertRepo;
    }

    @PostConstruct
    public void start() {
        // ── JWT Handshake Auth ───────────────────────────────────────────────
        server.addConnectListener(client -> {
            String token = client.getHandshakeData().getSingleUrlParam("token");
            if (token == null || token.isBlank()) {
                log.warn("Socket rejected: no token provided");
                client.disconnect();
                return;
            }

            Claims claims;
            try {
                claims = jwtUtil.parseClaims(token);
            } catch (Exception e) {
                log.warn("Socket rejected: invalid token — {}", e.getMessage());
                client.disconnect();
                return;
            }

            // Enforce absolute session cap (12h) on socket connections
            if (jwtUtil.isSessionExpired(claims)) {
                log.warn("Socket rejected: session expired for user {}", claims.get("username"));
                client.disconnect();
                return;
            }

            // Store user info on the client session
            int userId = claims.get("id", Integer.class);
            String role = claims.get("role", String.class);
            String name = claims.get("name", String.class);

            client.set("userId", userId);
            client.set("role", role);
            client.set("name", name);

            // Join role room and personal room
            client.joinRoom(role);
            client.joinRoom("user:" + userId);

            log.info("Socket connected: {} ({}) — socket {}", name, role, client.getSessionId());
        });

        // ── Missed-alert catchup ────────────────────────────────────────────
        server.addEventListener("alerts:catchup", Map.class, (client, data, ackSender) -> {
            try {
                String role = client.get("role");
                String sinceStr = data != null ? (String) data.get("since") : null;
                Instant sinceDate = sinceStr != null
                        ? Instant.parse(sinceStr)
                        : Instant.now().minus(24, ChronoUnit.HOURS);

                log.info("alerts:catchup from {} — since={}, sinceDate={}", role, sinceStr, sinceDate);

                List<Alert> missed = alertRepo.findByTimestampAfterOrderByTimestampAsc(sinceDate);

                log.info("alerts:catchup found {} missed alert(s)", missed.size());

                // Cap at 50
                if (missed.size() > 50) {
                    missed = missed.subList(0, 50);
                }

                if (!missed.isEmpty()) {
                    // Serialize alerts to maps for socket transport
                    List<Map<String, Object>> payload = missed.stream().map(a -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", a.getId());
                        m.put("senderId", a.getSenderId());
                        m.put("recipientScope", a.getRecipientScope().name());
                        m.put("message", a.getMessage());
                        m.put("timestamp", a.getTimestamp().toString());
                        m.put("isRead", a.getIsRead());
                        // Include sender info
                        if (a.getSender() != null) {
                            m.put("sender", Map.of(
                                    "id", a.getSender().getId(),
                                    "name", a.getSender().getName()
                            ));
                        }
                        return m;
                    }).collect(Collectors.toList());

                    client.sendEvent("alerts:missed", payload);
                }
            } catch (Exception e) {
                log.error("Socket alerts:catchup error: {}", e.getMessage());
            }
        });

        // ── Disconnect Handler ──────────────────────────────────────────────
        server.addDisconnectListener(client -> {
            Integer userId = client.get("userId");
            String name = client.get("name");
            String role = client.get("role");

            if (userId == null) return;

            log.info("Socket disconnected: {} ({}) — reason: client disconnect", name, role);

            // Notify MANAGER if no remaining connections for this user within 30s
            scheduler.schedule(() -> {
                try {
                    Collection<SocketIOClient> room = server.getRoomOperations("user:" + userId).getClients();
                    if (room == null || room.isEmpty()) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("userId", userId);
                        payload.put("name", name);
                        payload.put("role", role);
                        payload.put("timestamp", Instant.now().toString());

                        server.getRoomOperations("MANAGER").sendEvent("terminal:disconnected", payload);
                    }
                } catch (Exception e) {
                    log.error("Socket disconnect check error: {}", e.getMessage());
                }
            }, 30, TimeUnit.SECONDS);
        });

        server.start();
        log.info("Socket.IO server started on port {}", server.getConfiguration().getPort());
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        server.stop();
        log.info("Socket.IO server stopped");
    }

    // --- Emit helpers used by controllers ---

    // Send event to a specific room
    public void emitToRoom(String room, String event, Object data) {
        try {
            server.getRoomOperations(room).sendEvent(event, data);
        } catch (Exception e) {
            log.error("Socket failed to emit {} to room {}: {}", event, room, e.getMessage());
        }
    }

    // Send event to multiple rooms
    public void emitToRooms(List<String> rooms, String event, Object data) {
        for (String room : rooms) {
            emitToRoom(room, event, data);
        }
    }

    // Send to all roles
    public void emitToAllRoles(String event, Object data) {
        emitToRooms(List.of("BOH", "FOH", "MANAGER"), event, data);
    }

    // Send to BOH + MANAGER
    public void emitToBohAndManager(String event, Object data) {
        emitToRooms(List.of("BOH", "MANAGER"), event, data);
    }

    public SocketIOServer getServer() {
        return server;
    }
}
