package com.sos.controller;

import com.sos.dto.*;
import com.sos.model.Order;
import com.sos.model.OrderStatus;
import com.sos.security.DemoProtectionService;
import com.sos.security.JwtPrincipal;
import com.sos.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Order endpoints: create, list, update status, add notes
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DemoProtectionService demoProtection;

    public OrderController(OrderService orderService, DemoProtectionService demoProtection) {
        this.orderService = orderService;
        this.demoProtection = demoProtection;
    }

    // ── GET /api/orders ─────────────────────────────────────────────────────
    // BOH and MANAGER can see all orders (optionally filtered by status).
    @GetMapping
    @PreAuthorize("hasAnyRole('BOH', 'MANAGER')")
    public ResponseEntity<?> getOrders(@RequestParam(required = false) String status) {
        try {
            List<Order> orders = orderService.getAllOrders(status);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch orders."));
        }
    }

    // ── GET /api/orders/my ──────────────────────────────────────────────────
    // FOH sees only orders they submitted.
    @GetMapping("/my")
    @PreAuthorize("hasRole('FOH')")
    public ResponseEntity<?> getMyOrders(@AuthenticationPrincipal JwtPrincipal principal) {
        try {
            List<Order> orders = orderService.getMyOrders(principal.getId());
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch your orders."));
        }
    }

    // ── POST /api/orders ────────────────────────────────────────────────────
    // Only FOH can submit orders.
    @PostMapping
    @PreAuthorize("hasRole('FOH')")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest req,
                                         @AuthenticationPrincipal JwtPrincipal principal) {
        // Demo protection: cap active orders
        String capError = demoProtection.checkActiveOrderCap();
        if (capError != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse(capError));
        }

        try {
            Order order = orderService.createOrder(req, principal.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to submit order."));
        }
    }

    // ── PATCH /api/orders/:id/status ────────────────────────────────────────
    // BOH and MANAGER can change any status. FOH can only mark orders COMPLETED (checkout).
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('BOH', 'FOH', 'MANAGER')")
    public ResponseEntity<?> updateStatus(@PathVariable int id,
                                          @Valid @RequestBody UpdateStatusRequest req,
                                          @AuthenticationPrincipal JwtPrincipal principal,
                                          HttpServletRequest httpReq) {
        try {
            OrderStatus newStatus = OrderStatus.valueOf(req.getStatus());

            // FOH can only mark orders as COMPLETED (for table checkout)
            if ("FOH".equals(principal.getRole()) && newStatus != OrderStatus.COMPLETED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("FOH staff can only mark orders as completed."));
            }
            Order order = orderService.updateStatus(
                    id, newStatus, req.getEstimatedWait(), req.getPaymentMethod(),
                    req.getReceiptNumber(), req.getCardLast4(),
                    principal.getId(), principal.getRole(), httpReq.getRemoteAddr());
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update order status."));
        }
    }

    // ── PATCH /api/orders/:id/note ──────────────────────────────────────────
    // BOH sends a note to the FOH staff member who submitted the order.
    @PatchMapping("/{id}/note")
    @PreAuthorize("hasAnyRole('BOH', 'MANAGER')")
    public ResponseEntity<?> updateNote(@PathVariable int id,
                                        @RequestBody UpdateNoteRequest req) {
        if (req.getNote() == null || req.getNote().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Note text is required."));
        }
        try {
            Order order = orderService.updateNote(id, req.getNote());
            return ResponseEntity.ok(order);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send note."));
        }
    }

    // ── GET /api/orders/next-receipt ───────────────────────────────────────
    // Returns the next sequential receipt number for checkout batching.
    @GetMapping("/next-receipt")
    @PreAuthorize("hasAnyRole('FOH', 'MANAGER')")
    public ResponseEntity<?> getNextReceiptNumber() {
        return ResponseEntity.ok(Map.of("receiptNumber", orderService.generateNextReceiptNumber()));
    }

    // ── GET /api/orders/:id ─────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BOH', 'FOH', 'MANAGER')")
    public ResponseEntity<?> getOrderById(@PathVariable int id,
                                          @AuthenticationPrincipal JwtPrincipal principal) {
        try {
            Order order = orderService.getOrderById(id).orElse(null);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Order not found."));
            }
            // FOH users may only view their own orders (prevent IDOR)
            if ("FOH".equals(principal.getRole()) &&
                    !order.getSubmittedById().equals(principal.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Order not found."));
            }
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch order."));
        }
    }
}
