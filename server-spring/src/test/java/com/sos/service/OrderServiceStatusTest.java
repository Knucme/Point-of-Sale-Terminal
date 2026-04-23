package com.sos.service;

import com.sos.model.*;
import com.sos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for order status transitions in OrderService.
 * Uses Mockito to isolate the service from the database.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceStatusTest {

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

    private Order pendingOrder;

    @BeforeEach
    void setUp() throws Exception {
        pendingOrder = new Order();
        pendingOrder.setId(1);
        pendingOrder.setTableNumber(5);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setOrderItems(List.of());

        User submitter = new User();
        submitter.setId(10);
        submitter.setName("FOH Test");
        pendingOrder.setSubmittedBy(submitter);

        // submittedById is read-only in JPA; set via reflection for unit tests
        Field f = Order.class.getDeclaredField("submittedById");
        f.setAccessible(true);
        f.set(pendingOrder, 10);
    }

    // ── Valid Transitions ────────────────────────────────────────────────────

    @Test
    void pendingToInProgress_succeeds() {
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        Order result = orderService.updateStatus(1, OrderStatus.IN_PROGRESS, null, null, null, null, 2, "BOH", "127.0.0.1");
        assertEquals(OrderStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void pendingToCancelled_succeeds() {
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.updateStatus(1, OrderStatus.CANCELLED, null, null, null, null, 2, "MANAGER", "127.0.0.1");
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
    }

    // ── Invalid Transitions ─────────────────────────────────────────────────

    @Test
    void pendingToCompleted_succeeds() {
        // PENDING → COMPLETED is allowed (FOH table checkout)
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        Order result = orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CASH", null, null, 2, "FOH", "127.0.0.1");
        assertEquals(OrderStatus.COMPLETED, result.getStatus());
    }

    @Test
    void pendingToDelayed_throwsIllegalState() {
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));

        assertThrows(IllegalStateException.class, () ->
                orderService.updateStatus(1, OrderStatus.DELAYED, null, null, null, null, 2, "BOH", "127.0.0.1"));
    }

    @Test
    void completedToAnything_throwsIllegalState() {
        pendingOrder.setStatus(OrderStatus.COMPLETED);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));

        assertThrows(IllegalStateException.class, () ->
                orderService.updateStatus(1, OrderStatus.PENDING, null, null, null, null, 2, "BOH", "127.0.0.1"));
    }

    @Test
    void cancelledToAnything_throwsIllegalState() {
        pendingOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));

        assertThrows(IllegalStateException.class, () ->
                orderService.updateStatus(1, OrderStatus.IN_PROGRESS, null, null, null, null, 2, "BOH", "127.0.0.1"));
    }

    // ── Edge Cases ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_nonexistentOrder_throwsNoSuchElement() {
        when(orderRepo.findById(999)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                orderService.updateStatus(999, OrderStatus.IN_PROGRESS, null, null, null, null, 2, "BOH", "127.0.0.1"));
    }

    @Test
    void delayedOrder_setsEstimatedWait() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.updateStatus(1, OrderStatus.DELAYED, 15, null, null, null, 2, "BOH", "127.0.0.1");
        assertEquals(OrderStatus.DELAYED, result.getStatus());
        assertEquals(15, result.getEstimatedWait());
    }

    @Test
    void completedOrder_createsSOcketEvent() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CARD", null, null, 2, "BOH", "127.0.0.1");

        // Verify sales record creation was attempted
        verify(salesRecordRepo).save(any(SalesRecord.class));
        // Verify socket events sent to BOH + MANAGER
        verify(socketIO, atLeast(1)).emitToRoom(eq("BOH"), eq("order:statusChanged"), any());
        verify(socketIO, atLeast(1)).emitToRoom(eq("MANAGER"), eq("order:statusChanged"), any());
    }

    // ── Receipt Number Tests ────────────────────────────────────────────────

    @Test
    void generateNextReceiptNumber_incrementsFromMax() {
        when(orderRepo.findMaxReceiptNumber()).thenReturn("00041");

        String next = orderService.generateNextReceiptNumber();
        assertEquals("00042", next);
    }

    @Test
    void generateNextReceiptNumber_startsFromZero() {
        when(orderRepo.findMaxReceiptNumber()).thenReturn("00000");

        String next = orderService.generateNextReceiptNumber();
        assertEquals("00001", next);
    }

    @Test
    void generateNextReceiptNumber_zeroPads() {
        when(orderRepo.findMaxReceiptNumber()).thenReturn("00009");

        String next = orderService.generateNextReceiptNumber();
        assertEquals("00010", next);
    }

    @Test
    void completedOrder_autoGeneratesReceiptNumber() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());
        when(orderRepo.findMaxReceiptNumber()).thenReturn("00005");

        Order result = orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CASH", null, null, 2, "FOH", "127.0.0.1");
        assertEquals("00006", result.getReceiptNumber());
    }

    @Test
    void completedOrder_usesProvidedReceiptNumber() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        Order result = orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CASH", "00099", null, 2, "FOH", "127.0.0.1");
        assertEquals("00099", result.getReceiptNumber());
    }

    // ── Card Last 4 Storage Tests ───────────────────────────────────────────

    @Test
    void completedOrder_storesCardLast4InSalesRecord() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CREDIT", "00001", "4567", 2, "FOH", "127.0.0.1");

        // Capture the SalesRecord that was saved
        var captor = org.mockito.ArgumentCaptor.forClass(SalesRecord.class);
        verify(salesRecordRepo).save(captor.capture());
        SalesRecord saved = captor.getValue();
        assertEquals("4567", saved.getCardLast4());
        assertEquals("CREDIT", saved.getPaymentMethod());
    }

    @Test
    void completedOrder_noCardLast4ForCash() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CASH", null, null, 2, "FOH", "127.0.0.1");

        var captor = org.mockito.ArgumentCaptor.forClass(SalesRecord.class);
        verify(salesRecordRepo).save(captor.capture());
        SalesRecord saved = captor.getValue();
        assertNull(saved.getCardLast4());
        assertEquals("CASH", saved.getPaymentMethod());
    }

    @Test
    void completedOrder_defaultsToCashWhenNoPaymentMethod() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.empty());
        when(orderItemRepo.findByOrderId(1)).thenReturn(List.of());

        orderService.updateStatus(1, OrderStatus.COMPLETED, null, null, null, null, 2, "FOH", "127.0.0.1");

        var captor = org.mockito.ArgumentCaptor.forClass(SalesRecord.class);
        verify(salesRecordRepo).save(captor.capture());
        assertEquals("CASH", captor.getValue().getPaymentMethod());
    }

    @Test
    void completedOrder_skipsSalesRecordIfAlreadyExists() {
        pendingOrder.setStatus(OrderStatus.IN_PROGRESS);
        when(orderRepo.findById(1)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesRecordRepo.findByOrderId(1)).thenReturn(Optional.of(new SalesRecord()));

        orderService.updateStatus(1, OrderStatus.COMPLETED, null, "CASH", null, null, 2, "FOH", "127.0.0.1");

        // save should NOT be called since record already exists
        verify(salesRecordRepo, never()).save(any(SalesRecord.class));
    }
}
