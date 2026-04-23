package com.sos.controller;

import com.sos.config.GlobalExceptionHandler;
import com.sos.model.AvailabilityStatus;
import com.sos.model.Inventory;
import com.sos.model.MenuItem;
import com.sos.repository.InventoryRepository;
import com.sos.repository.MenuItemRepository;
import com.sos.security.JwtPrincipal;
import com.sos.service.SecurityLogService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Tests for menu CRUD endpoints (create, read, update, delete)
@ExtendWith(MockitoExtension.class)
class MenuControllerTest {

    private MockMvc mvc;

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private SecurityLogService securityLog;
    @Mock private SocketIOService socketIO;

    private JwtPrincipal managerPrincipal;

    @BeforeEach
    void setUp() {
        MenuController controller = new MenuController(menuItemRepo, inventoryRepo, securityLog, socketIO);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        managerPrincipal = new JwtPrincipal(1, "manager", "MANAGER", "Alex Manager", System.currentTimeMillis() / 1000);
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

    private MenuItem sampleMenuItem() {
        MenuItem item = new MenuItem();
        item.setId(1);
        item.setName("Classic Burger");
        item.setCategory("Mains");
        item.setPrice(new BigDecimal("11.99"));
        item.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return item;
    }

    // ── GET /api/menu ───────────────────────────────────────────────────────

    @Test
    void getMenu_returnsAllItems() throws Exception {
        MenuItem burger = sampleMenuItem();
        when(menuItemRepo.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(burger));

        mvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Classic Burger"))
                .andExpect(jsonPath("$[0].price").value(11.99));
    }

    // ── POST /api/menu ──────────────────────────────────────────────────────

    @Test
    void createMenuItem_validRequest_returns201() throws Exception {
        when(menuItemRepo.findByNameIgnoreCaseAndAvailabilityStatus(any(), any()))
                .thenReturn(Optional.empty());
        when(menuItemRepo.save(any())).thenAnswer(inv -> {
            MenuItem m = inv.getArgument(0);
            m.setId(5);
            return m;
        });
        when(menuItemRepo.findById(5)).thenAnswer(inv -> {
            MenuItem m = sampleMenuItem();
            m.setId(5);
            m.setName("New Item");
            return Optional.of(m);
        });

        mvc.perform(post("/api/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Item\",\"category\":\"Mains\",\"price\":9.99}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Item"));
    }

    @Test
    void createMenuItem_negativePrice_returns400() throws Exception {
        mvc.perform(post("/api/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad Item\",\"category\":\"Mains\",\"price\":-5.00}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMenuItem_duplicateName_returns409() throws Exception {
        MenuItem existing = sampleMenuItem();
        when(menuItemRepo.findByNameIgnoreCaseAndAvailabilityStatus(any(), any()))
                .thenReturn(Optional.of(existing));

        mvc.perform(post("/api/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Classic Burger\",\"category\":\"Mains\",\"price\":11.99}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isConflict());
    }

    @Test
    void createMenuItem_missingName_returns400() throws Exception {
        mvc.perform(post("/api/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Mains\",\"price\":9.99}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/menu/:id ───────────────────────────────────────────────────

    @Test
    void getMenuItem_existingId_returnsItem() throws Exception {
        when(menuItemRepo.findById(1)).thenReturn(Optional.of(sampleMenuItem()));

        mvc.perform(get("/api/menu/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Classic Burger"));
    }

    @Test
    void getMenuItem_nonexistentId_returns404() throws Exception {
        when(menuItemRepo.findById(999)).thenReturn(Optional.empty());

        mvc.perform(get("/api/menu/999"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/menu/:id ────────────────────────────────────────────────

    @Test
    void deleteMenuItem_existingId_returns204() throws Exception {
        when(menuItemRepo.findById(1)).thenReturn(Optional.of(sampleMenuItem()));

        mvc.perform(delete("/api/menu/1")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isNoContent());

        verify(menuItemRepo).delete(any(MenuItem.class));
    }

    @Test
    void deleteMenuItem_nonexistentId_returns404() throws Exception {
        when(menuItemRepo.findById(999)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/menu/999")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isNotFound());
    }
}
