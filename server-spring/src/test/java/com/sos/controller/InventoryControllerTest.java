package com.sos.controller;

import com.sos.config.GlobalExceptionHandler;
import com.sos.model.AvailabilityStatus;
import com.sos.model.Inventory;
import com.sos.model.MenuItem;
import com.sos.repository.InventoryRepository;
import com.sos.repository.MenuItemRepository;
import com.sos.repository.OrderItemRepository;
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

// Tests for inventory endpoints — create, restock, and low stock detection
@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private MockMvc mvc;

    @Mock private InventoryRepository inventoryRepo;
    @Mock private MenuItemRepository menuItemRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private SecurityLogService securityLog;
    @Mock private SocketIOService socketIO;

    private JwtPrincipal managerPrincipal;
    private JwtPrincipal bohPrincipal;

    @BeforeEach
    void setUp() {
        InventoryController controller = new InventoryController(
                inventoryRepo, menuItemRepo, orderItemRepo, securityLog, socketIO);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        managerPrincipal = new JwtPrincipal(1, "manager", "MANAGER", "Alex Manager", System.currentTimeMillis() / 1000);
        bohPrincipal = new JwtPrincipal(2, "boh_cook", "BOH", "Sam Cook", System.currentTimeMillis() / 1000);
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

    private Inventory sampleInventory() {
        Inventory inv = new Inventory();
        inv.setId(1);
        inv.setItemName("French Fries");
        inv.setQuantity(new BigDecimal("20.000"));
        inv.setUnit("qty");
        inv.setLowStockThreshold(new BigDecimal("5.000"));
        inv.setRestockAmount(new BigDecimal("20.000"));
        return inv;
    }

    // ── POST /api/inventory ─────────────────────────────────────────────────

    @Test
    void createInventory_validRequest_returns201() throws Exception {
        when(inventoryRepo.findByItemNameIgnoreCase("Chicken Wings")).thenReturn(Optional.empty());
        when(inventoryRepo.save(any())).thenAnswer(inv -> {
            Inventory i = inv.getArgument(0);
            i.setId(10);
            return i;
        });

        mvc.perform(post("/api/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemName\":\"Chicken Wings\",\"quantity\":15,\"unit\":\"qty\",\"lowStockThreshold\":3,\"restockAmount\":10}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemName").value("Chicken Wings"));
    }

    @Test
    void createInventory_negativeQuantity_returns400() throws Exception {
        mvc.perform(post("/api/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemName\":\"Bad Item\",\"quantity\":-5,\"unit\":\"qty\",\"lowStockThreshold\":3}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInventory_duplicateName_returns409() throws Exception {
        when(inventoryRepo.findByItemNameIgnoreCase("French Fries"))
                .thenReturn(Optional.of(sampleInventory()));

        mvc.perform(post("/api/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemName\":\"French Fries\",\"quantity\":10,\"unit\":\"qty\",\"lowStockThreshold\":3}")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isConflict());
    }

    // ── POST /api/inventory/:id/restock ─────────────────────────────────────

    @Test
    void restock_addsRestockAmount() throws Exception {
        Inventory inv = sampleInventory();
        inv.setQuantity(new BigDecimal("3.000")); // below threshold
        when(inventoryRepo.findById(1)).thenReturn(Optional.of(inv));
        when(inventoryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/api/inventory/1/restock")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(23.0)) // 3 + 20
                .andExpect(jsonPath("$.wasEmpty").value(false));
    }

    @Test
    void restock_emptyItem_reEnablesLinkedMenuItems() throws Exception {
        Inventory inv = sampleInventory();
        inv.setQuantity(BigDecimal.ZERO); // completely out
        when(inventoryRepo.findById(1)).thenReturn(Optional.of(inv));
        when(inventoryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        MenuItem linkedItem = new MenuItem();
        linkedItem.setId(5);
        linkedItem.setName("French Fries");
        linkedItem.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        when(menuItemRepo.findByInventoryItemId(1)).thenReturn(List.of(linkedItem));
        when(menuItemRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/api/inventory/1/restock")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasEmpty").value(true));

        // Verify the linked menu item was re-enabled
        verify(menuItemRepo).save(argThat(m ->
                m.getAvailabilityStatus() == AvailabilityStatus.AVAILABLE));
        // Verify socket event was sent
        verify(socketIO).emitToAllRoles(eq("item:available"), any());
    }

    @Test
    void restock_nonexistentItem_returns404() throws Exception {
        when(inventoryRepo.findById(999)).thenReturn(Optional.empty());

        mvc.perform(post("/api/inventory/999/restock")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/inventory ──────────────────────────────────────────────────

    @Test
    void getAll_annotatesLowStockFlag() throws Exception {
        Inventory lowStock = sampleInventory();
        lowStock.setQuantity(new BigDecimal("2.000")); // below threshold of 5

        Inventory normalStock = sampleInventory();
        normalStock.setId(2);
        normalStock.setItemName("Draft Beer");
        normalStock.setQuantity(new BigDecimal("24.000"));

        when(inventoryRepo.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(lowStock, normalStock));

        mvc.perform(get("/api/inventory")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isLowStock").value(true))
                .andExpect(jsonPath("$[1].isLowStock").value(false));
    }
}
