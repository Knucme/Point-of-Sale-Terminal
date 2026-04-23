package com.sos.service;

import com.sos.dto.CreateOrderRequest;
import com.sos.model.*;
import com.sos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for order creation (submitting a new order from FOH)
@ExtendWith(MockitoExtension.class)
class OrderServiceCreateTest {

    @Mock private OrderRepository orderRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private MenuItemRepository menuItemRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private SalesRecordRepository salesRecordRepo;
    @Mock private UserRepository userRepo;
    @Mock private SecurityLogService securityLog;
    @Mock private SocketIOService socketIO;

    @InjectMocks
    private OrderService orderService;

    private User fohUser;
    private MenuItem burger;
    private MenuItem fries;

    @BeforeEach
    void setUp() {
        fohUser = new User();
        fohUser.setId(10);
        fohUser.setName("Jordan Server");
        fohUser.setUsername("foh_server");
        fohUser.setRole(Role.FOH);

        burger = new MenuItem();
        burger.setId(1);
        burger.setName("Classic Burger");
        burger.setCategory("Mains");
        burger.setPrice(new BigDecimal("11.99"));
        burger.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        fries = new MenuItem();
        fries.setId(2);
        fries.setName("French Fries");
        fries.setCategory("Sides");
        fries.setPrice(new BigDecimal("4.99"));
        fries.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
    }

    // ── Successful order creation ───────────────────────────────────────────

    @Test
    void createOrder_validItems_succeeds() {
        when(userRepo.findById(10)).thenReturn(Optional.of(fohUser));
        when(menuItemRepo.findByIdIn(List.of(1, 2))).thenReturn(List.of(burger, fries));
        when(orderRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100);
            return o;
        });

        CreateOrderRequest req = new CreateOrderRequest();
        req.setTableNumber(5);
        CreateOrderRequest.OrderItemRequest item1 = new CreateOrderRequest.OrderItemRequest();
        item1.setMenuItemId(1);
        item1.setQuantity(2);
        CreateOrderRequest.OrderItemRequest item2 = new CreateOrderRequest.OrderItemRequest();
        item2.setMenuItemId(2);
        item2.setQuantity(1);
        req.setItems(List.of(item1, item2));

        Order result = orderService.createOrder(req, 10);

        assertEquals(5, result.getTableNumber());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(2, result.getOrderItems().size());
        // Verify socket events were sent to BOH and MANAGER
        verify(socketIO).emitToRoom(eq("BOH"), eq("order:new"), any());
        verify(socketIO).emitToRoom(eq("MANAGER"), eq("order:new"), any());
    }

    @Test
    void createOrder_withSpecialInstructions_savesInstructions() {
        when(userRepo.findById(10)).thenReturn(Optional.of(fohUser));
        when(menuItemRepo.findByIdIn(List.of(1))).thenReturn(List.of(burger));
        when(orderRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(101);
            return o;
        });

        CreateOrderRequest req = new CreateOrderRequest();
        req.setTableNumber(3);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(1);
        item.setQuantity(1);
        item.setSpecialInstructions("No onions please");
        req.setItems(List.of(item));

        Order result = orderService.createOrder(req, 10);

        assertEquals("No onions please", result.getOrderItems().get(0).getSpecialInstructions());
    }

    // ── Validation failures ─────────────────────────────────────────────────

    @Test
    void createOrder_nonexistentMenuItem_throwsIllegalArgument() {
        when(menuItemRepo.findByIdIn(List.of(999))).thenReturn(List.of()); // not found

        CreateOrderRequest req = new CreateOrderRequest();
        req.setTableNumber(1);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(999);
        req.setItems(List.of(item));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(req, 10));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void createOrder_unavailableItem_throwsIllegalArgument() {
        burger.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        when(menuItemRepo.findByIdIn(List.of(1))).thenReturn(List.of(burger));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setTableNumber(1);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(1);
        req.setItems(List.of(item));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(req, 10));
        assertTrue(ex.getMessage().contains("unavailable"));
    }

    @Test
    void createOrder_quantityDefaultsToOne() {
        when(userRepo.findById(10)).thenReturn(Optional.of(fohUser));
        when(menuItemRepo.findByIdIn(List.of(1))).thenReturn(List.of(burger));
        when(orderRepo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(102);
            return o;
        });

        CreateOrderRequest req = new CreateOrderRequest();
        req.setTableNumber(2);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(1);
        // dont set quantity — should default to 1
        req.setItems(List.of(item));

        Order result = orderService.createOrder(req, 10);

        assertEquals(1, result.getOrderItems().get(0).getQuantity());
    }
}
