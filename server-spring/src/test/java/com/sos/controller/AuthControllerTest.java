package com.sos.controller;

import com.sos.config.GlobalExceptionHandler;
import com.sos.model.Role;
import com.sos.model.User;
import com.sos.model.UserStatus;
import com.sos.repository.UserRepository;
import com.sos.security.JwtUtil;
import com.sos.security.LoginRateLimiter;
import com.sos.service.SecurityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for the login endpoint — no Spring context or DB needed.
 * Covers UC-01: successful login, wrong password, unknown user, inactive account.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mvc;

    @Mock private UserRepository userRepo;
    @Mock private SecurityLogService securityLog;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private JwtUtil jwtUtil;
    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        String secret = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
                       + "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        jwtUtil = new JwtUtil(secret, 600_000, 43_200);
        rateLimiter = new LoginRateLimiter(10, 300_000);

        AuthController controller = new AuthController(userRepo, encoder, jwtUtil, rateLimiter, securityLog);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private User activeUser(String username, String password) {
        User u = new User();
        u.setId(1);
        u.setName("Test User");
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(Role.MANAGER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    // ── UC-01: Successful login ─────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        User user = activeUser("manager", "Manager-Dev-2026");
        when(userRepo.findByUsername("manager")).thenReturn(Optional.of(user));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager\",\"password\":\"Manager-Dev-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("manager"))
                .andExpect(jsonPath("$.user.role").value("MANAGER"));
    }

    // ── UC-01 Ext 1: Wrong password ─────────────────────────────────────────

    @Test
    void login_wrongPassword_returns401() throws Exception {
        User user = activeUser("manager", "Manager-Dev-2026");
        when(userRepo.findByUsername("manager")).thenReturn(Optional.of(user));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager\",\"password\":\"WrongPass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password."));
    }

    // ── UC-01 Ext 2: Unknown user ───────────────────────────────────────────

    @Test
    void login_unknownUser_returns401() throws Exception {
        when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"SomePass1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password."));
    }

    // ── UC-11 Ext 1a: Inactive account ──────────────────────────────────────

    @Test
    void login_inactiveAccount_returns403() throws Exception {
        User user = activeUser("disabled", "Password1234");
        user.setStatus(UserStatus.INACTIVE);
        when(userRepo.findByUsername("disabled")).thenReturn(Optional.of(user));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"disabled\",\"password\":\"Password1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Account is not active. Please contact your manager."));
    }

    // ── Missing fields ──────────────────────────────────────────────────────

    @Test
    void login_missingUsername_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SomePass1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Malformed JSON ──────────────────────────────────────────────────────

    @Test
    void login_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json"))
                .andExpect(status().isBadRequest());
    }
}
