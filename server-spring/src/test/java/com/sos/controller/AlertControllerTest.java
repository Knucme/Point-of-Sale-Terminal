package com.sos.controller;

import com.sos.config.GlobalExceptionHandler;
import com.sos.model.Alert;
import com.sos.model.RecipientScope;
import com.sos.model.Role;
import com.sos.model.User;
import com.sos.repository.AlertRepository;
import com.sos.repository.UserRepository;
import com.sos.security.JwtPrincipal;
import com.sos.service.SocketIOService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Tests for alert endpoints — create, mark read, and permission checks
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    private MockMvc mvc;

    @Mock private AlertRepository alertRepo;
    @Mock private UserRepository userRepo;
    @Mock private SocketIOService socketIO;

    private JwtPrincipal bohPrincipal;
    private JwtPrincipal managerPrincipal;
    private JwtPrincipal fohPrincipal;
    private User bohUser;

    @BeforeEach
    void setUp() {
        AlertController controller = new AlertController(alertRepo, userRepo, socketIO);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        bohPrincipal = new JwtPrincipal(2, "boh_cook", "BOH", "Sam Cook", System.currentTimeMillis() / 1000);
        managerPrincipal = new JwtPrincipal(1, "manager", "MANAGER", "Alex Manager", System.currentTimeMillis() / 1000);
        fohPrincipal = new JwtPrincipal(3, "foh_server", "FOH", "Jordan Server", System.currentTimeMillis() / 1000);

        bohUser = new User();
        bohUser.setId(2);
        bohUser.setName("Sam Cook");
        bohUser.setRole(Role.BOH);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Sets the SecurityContext so @AuthenticationPrincipal resolves correctly. */
    private RequestPostProcessor authenticated(JwtPrincipal p, String role) {
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            p, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            return request;
        };
    }

    private Alert sampleAlert(int senderId) {
        Alert a = new Alert();
        a.setId(1);
        a.setSender(bohUser);
        a.setRecipientScope(RecipientScope.BROADCAST);
        a.setMessage("Table 5 food is ready");
        a.setTimestamp(Instant.now());
        a.setIsRead(false);
        // Set senderId via reflection since its read-only in JPA
        try {
            java.lang.reflect.Field f = Alert.class.getDeclaredField("senderId");
            f.setAccessible(true);
            f.set(a, senderId);
        } catch (Exception e) { throw new RuntimeException(e); }
        return a;
    }

    // ── POST /api/alerts ────────────────────────────────────────────────────

    @Test
    void createAlert_broadcast_returns201() throws Exception {
        when(userRepo.findById(2)).thenReturn(Optional.of(bohUser));
        when(alertRepo.save(any())).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(10);
            return a;
        });
        when(alertRepo.findById(10)).thenAnswer(inv -> {
            Alert a = sampleAlert(2);
            a.setId(10);
            return Optional.of(a);
        });

        mvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Table 5 food is ready\",\"recipientScope\":\"BROADCAST\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isCreated());

        // Verify socket events sent to FOH and MANAGER
        verify(socketIO).emitToRoom(eq("FOH"), eq("alert:broadcast"), any());
        verify(socketIO).emitToRoom(eq("MANAGER"), eq("alert:broadcast"), any());
    }

    @Test
    void createAlert_specificWithoutTargetUser_returns400() throws Exception {
        mvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hey\",\"recipientScope\":\"SPECIFIC\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAlert_invalidScope_returns400() throws Exception {
        mvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hey\",\"recipientScope\":\"INVALID\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/alerts/:id/read ──────────────────────────────────────────

    @Test
    void markAsRead_bySender_succeeds() throws Exception {
        Alert alert = sampleAlert(2); // sender is BOH user (id=2)
        when(alertRepo.findById(1)).thenReturn(Optional.of(alert));
        when(alertRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/alerts/1/read")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk());
    }

    @Test
    void markAsRead_byManager_succeeds() throws Exception {
        Alert alert = sampleAlert(2); // sender is BOH, but manager can also mark read
        when(alertRepo.findById(1)).thenReturn(Optional.of(alert));
        when(alertRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/alerts/1/read")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void markAsRead_byOtherUser_returns403() throws Exception {
        Alert alert = sampleAlert(2); // sender is BOH (id=2)
        when(alertRepo.findById(1)).thenReturn(Optional.of(alert));

        // FOH user (id=3) tries to mark it read — not the sender and not manager
        mvc.perform(patch("/api/alerts/1/read")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsRead_nonexistentAlert_returns404() throws Exception {
        when(alertRepo.findById(999)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/alerts/999/read")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isNotFound());
    }
}
