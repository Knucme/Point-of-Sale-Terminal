package com.sos.controller;

import com.sos.config.GlobalExceptionHandler;
import com.sos.model.Order;
import com.sos.model.OrderStatus;
import com.sos.security.JwtPrincipal;
import com.sos.service.OrderService;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for OrderController endpoints:
 * - GET /api/orders (all orders)
 * - GET /api/orders/my (FOH own orders)
 * - POST /api/orders (create order)
 * - PATCH /api/orders/{id}/status (status transitions, FOH restrictions, payment)
 * - PATCH /api/orders/{id}/note (BOH notes)
 * - GET /api/orders/next-receipt (receipt number generation)
 * - GET /api/orders/{id} (single order + IDOR protection)
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mvc;

    @Mock private OrderService orderService;

    private JwtPrincipal managerPrincipal;
    private JwtPrincipal bohPrincipal;
    private JwtPrincipal fohPrincipal;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(orderService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        managerPrincipal = new JwtPrincipal(1, "manager", "MANAGER", "Alex Rivera", System.currentTimeMillis() / 1000);
        bohPrincipal = new JwtPrincipal(2, "boh", "BOH", "Marco Chen", System.currentTimeMillis() / 1000);
        fohPrincipal = new JwtPrincipal(3, "foh", "FOH", "Sofia Patel", System.currentTimeMillis() / 1000);
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

    private Order sampleOrder(int id, int tableNumber, OrderStatus status, int submittedById) {
        Order order = new Order();
        order.setId(id);
        order.setTableNumber(tableNumber);
        order.setStatus(status);
        order.setOrderItems(List.of());

        // Use reflection to set submittedById (read-only in JPA)
        try {
            var f = Order.class.getDeclaredField("submittedById");
            f.setAccessible(true);
            f.set(order, submittedById);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    // ── GET /api/orders ────────────────────────────────────────────────────

    @Test
    void getOrders_returnsAllOrders() throws Exception {
        Order o1 = sampleOrder(1, 5, OrderStatus.PENDING, 3);
        Order o2 = sampleOrder(2, 8, OrderStatus.IN_PROGRESS, 3);
        when(orderService.getAllOrders(null)).thenReturn(List.of(o1, o2));

        mvc.perform(get("/api/orders")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getOrders_filtersByStatus() throws Exception {
        Order o1 = sampleOrder(1, 5, OrderStatus.PENDING, 3);
        when(orderService.getAllOrders("PENDING")).thenReturn(List.of(o1));

        mvc.perform(get("/api/orders").param("status", "PENDING")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── GET /api/orders/my ─────────────────────────────────────────────────

    @Test
    void getMyOrders_returnsFohUsersOrders() throws Exception {
        Order o1 = sampleOrder(1, 5, OrderStatus.PENDING, fohPrincipal.getId());
        when(orderService.getMyOrders(fohPrincipal.getId())).thenReturn(List.of(o1));

        mvc.perform(get("/api/orders/my")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── PATCH /api/orders/{id}/status ──────────────────────────────────────

    @Test
    void updateStatus_bohToInProgress_succeeds() throws Exception {
        Order updated = sampleOrder(1, 5, OrderStatus.IN_PROGRESS, 3);
        when(orderService.updateStatus(eq(1), eq(OrderStatus.IN_PROGRESS),
                isNull(), isNull(), isNull(), isNull(),
                eq(bohPrincipal.getId()), eq("BOH"), any()))
                .thenReturn(updated);

        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateStatus_fohCompleted_succeeds() throws Exception {
        Order updated = sampleOrder(1, 5, OrderStatus.COMPLETED, 3);
        updated.setReceiptNumber("00001");
        when(orderService.updateStatus(eq(1), eq(OrderStatus.COMPLETED),
                isNull(), eq("CASH"), eq("00001"), isNull(),
                eq(fohPrincipal.getId()), eq("FOH"), any()))
                .thenReturn(updated);

        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\",\"paymentMethod\":\"CASH\",\"receiptNumber\":\"00001\"}")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void updateStatus_fohCompletedWithCard_passesCardLast4() throws Exception {
        Order updated = sampleOrder(1, 5, OrderStatus.COMPLETED, 3);
        updated.setReceiptNumber("00002");
        when(orderService.updateStatus(eq(1), eq(OrderStatus.COMPLETED),
                isNull(), eq("CREDIT"), eq("00002"), eq("1234"),
                eq(fohPrincipal.getId()), eq("FOH"), any()))
                .thenReturn(updated);

        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\",\"paymentMethod\":\"CREDIT\",\"receiptNumber\":\"00002\",\"cardLast4\":\"1234\"}")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_fohNonCompleted_returns403() throws Exception {
        // FOH can only mark orders as COMPLETED — anything else is forbidden
        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_fohCancelled_returns403() throws Exception {
        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_invalidStatusEnum_returns400() throws Exception {
        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID_STATUS\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_orderNotFound_returns404() throws Exception {
        when(orderService.updateStatus(eq(999), any(), any(), any(), any(), any(),
                anyInt(), any(), any()))
                .thenThrow(new NoSuchElementException("Order not found."));

        mvc.perform(patch("/api/orders/999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_invalidTransition_returns400() throws Exception {
        when(orderService.updateStatus(eq(1), eq(OrderStatus.DELAYED), any(), any(), any(), any(),
                anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("Cannot move order from PENDING to DELAYED."));

        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELAYED\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_withEstimatedWait_passesValue() throws Exception {
        Order updated = sampleOrder(1, 5, OrderStatus.DELAYED, 3);
        updated.setEstimatedWait(15);
        when(orderService.updateStatus(eq(1), eq(OrderStatus.DELAYED),
                eq(15), isNull(), isNull(), isNull(),
                eq(bohPrincipal.getId()), eq("BOH"), any()))
                .thenReturn(updated);

        mvc.perform(patch("/api/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELAYED\",\"estimatedWait\":15}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedWait").value(15));
    }

    // ── PATCH /api/orders/{id}/note ────────────────────────────────────────

    @Test
    void updateNote_validNote_succeeds() throws Exception {
        Order updated = sampleOrder(1, 5, OrderStatus.IN_PROGRESS, 3);
        updated.setBohNote("Extra ketchup");
        when(orderService.updateNote(1, "Extra ketchup")).thenReturn(updated);

        mvc.perform(patch("/api/orders/1/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Extra ketchup\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bohNote").value("Extra ketchup"));
    }

    @Test
    void updateNote_emptyNote_returns400() throws Exception {
        mvc.perform(patch("/api/orders/1/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateNote_nullNote_returns400() throws Exception {
        mvc.perform(patch("/api/orders/1/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateNote_orderNotFound_returns404() throws Exception {
        when(orderService.updateNote(eq(999), any()))
                .thenThrow(new NoSuchElementException("Order not found."));

        mvc.perform(patch("/api/orders/999/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Some note\"}")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/orders/next-receipt ───────────────────────────────────────

    @Test
    void getNextReceiptNumber_returnsSequentialNumber() throws Exception {
        when(orderService.generateNextReceiptNumber()).thenReturn("00042");

        mvc.perform(get("/api/orders/next-receipt")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value("00042"));
    }

    @Test
    void getNextReceiptNumber_managerCanAccess() throws Exception {
        when(orderService.generateNextReceiptNumber()).thenReturn("00001");

        mvc.perform(get("/api/orders/next-receipt")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value("00001"));
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────

    @Test
    void getOrderById_bohCanSeeAnyOrder() throws Exception {
        Order order = sampleOrder(1, 5, OrderStatus.PENDING, fohPrincipal.getId());
        when(orderService.getOrderById(1)).thenReturn(Optional.of(order));

        mvc.perform(get("/api/orders/1")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getOrderById_fohCanSeeOwnOrder() throws Exception {
        Order order = sampleOrder(1, 5, OrderStatus.PENDING, fohPrincipal.getId());
        when(orderService.getOrderById(1)).thenReturn(Optional.of(order));

        mvc.perform(get("/api/orders/1")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getOrderById_fohCannotSeeOtherUsersOrder_idor() throws Exception {
        // Order belongs to user 99, FOH user is 3 — IDOR prevention
        Order order = sampleOrder(1, 5, OrderStatus.PENDING, 99);
        when(orderService.getOrderById(1)).thenReturn(Optional.of(order));

        mvc.perform(get("/api/orders/1")
                .with(authenticated(fohPrincipal, "FOH")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        when(orderService.getOrderById(999)).thenReturn(Optional.empty());

        mvc.perform(get("/api/orders/999")
                .with(authenticated(bohPrincipal, "BOH")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderById_managerCanSeeAnyOrder() throws Exception {
        Order order = sampleOrder(1, 5, OrderStatus.PENDING, fohPrincipal.getId());
        when(orderService.getOrderById(1)).thenReturn(Optional.of(order));

        mvc.perform(get("/api/orders/1")
                .with(authenticated(managerPrincipal, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
