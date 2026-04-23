package com.sos.service;

import com.sos.dto.CreateOrderRequest;
import com.sos.model.*;
import com.sos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

// Handles order creation, status updates, inventory decrements, and sales records.
@Service
public class OrderService {

    // Valid status transitions
    private static final Map<OrderStatus, List<OrderStatus>> STATUS_TRANSITIONS = Map.of(
        OrderStatus.PENDING,     List.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELLED),
        OrderStatus.IN_PROGRESS, List.of(OrderStatus.DELAYED, OrderStatus.COMPLETED, OrderStatus.CANCELLED),
        OrderStatus.DELAYED,     List.of(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELLED),
        OrderStatus.COMPLETED,   List.of(),
        OrderStatus.CANCELLED,   List.of()
    );

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final MenuItemRepository menuItemRepo;
    private final InventoryRepository inventoryRepo;
    private final SalesRecordRepository salesRecordRepo;
    private final UserRepository userRepo;
    private final SecurityLogService securityLog;
    private final SocketIOService socketIO;

    public OrderService(OrderRepository orderRepo,
                        OrderItemRepository orderItemRepo,
                        MenuItemRepository menuItemRepo,
                        InventoryRepository inventoryRepo,
                        SalesRecordRepository salesRecordRepo,
                        UserRepository userRepo,
                        SecurityLogService securityLog,
                        SocketIOService socketIO) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.menuItemRepo = menuItemRepo;
        this.inventoryRepo = inventoryRepo;
        this.salesRecordRepo = salesRecordRepo;
        this.userRepo = userRepo;
        this.securityLog = securityLog;
        this.socketIO = socketIO;
    }

    // Get all orders, optionally filtered by status
    public List<Order> getAllOrders(String status) {
        if (status != null && !status.isBlank()) {
            try {
                OrderStatus os = OrderStatus.valueOf(status);
                return orderRepo.findByStatusOrderByCreatedAtDesc(os);
            } catch (IllegalArgumentException e) {
                return orderRepo.findAllByOrderByCreatedAtDesc();
            }
        }
        return orderRepo.findAllByOrderByCreatedAtDesc();
    }

    // Get orders for a specific FOH user
    public List<Order> getMyOrders(int userId) {
        return orderRepo.findBySubmittedByIdOrderByCreatedAtDesc(userId);
    }

    // Get a single order by ID
    public Optional<Order> getOrderById(int id) {
        return orderRepo.findById(id);
    }

    // Create a new order — validates menu items, saves to DB, emits socket event
    @Transactional
    public Order createOrder(CreateOrderRequest req, int userId) {
        // Check all requested items exist and are available
        List<Integer> menuItemIds = req.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getMenuItemId)
                .distinct()
                .collect(Collectors.toList());

        List<MenuItem> menuItems = menuItemRepo.findByIdIn(menuItemIds);
        if (menuItems.size() != menuItemIds.size()) {
            throw new IllegalArgumentException("One or more menu items were not found.");
        }

        List<MenuItem> unavailable = menuItems.stream()
                .filter(m -> m.getAvailabilityStatus() == AvailabilityStatus.UNAVAILABLE)
                .collect(Collectors.toList());
        if (!unavailable.isEmpty()) {
            String names = unavailable.stream().map(MenuItem::getName).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("These items are currently unavailable: " + names + ".");
        }

        // Create order
        User submitter = userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found."));
        Order order = new Order();
        order.setTableNumber(req.getTableNumber());
        order.setSubmittedBy(submitter);
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> orderItems = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest item : req.getItems()) {
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            MenuItem mi = menuItems.stream()
                    .filter(m -> m.getId().equals(item.getMenuItemId()))
                    .findFirst().orElseThrow();
            oi.setMenuItem(mi);
            oi.setQuantity(Math.max(1, item.getQuantity() != null ? item.getQuantity() : 1));
            oi.setSpecialInstructions(
                    item.getSpecialInstructions() != null ? item.getSpecialInstructions().trim() : null);
            oi.setPrepStatus("WAITING");
            orderItems.add(oi);
        }
        order.setOrderItems(orderItems);

        Order saved = orderRepo.save(order);

        // ── Socket: order:new → BOH + MANAGER ───────────────────────────────
        Map<String, Object> orderPayload = serializeOrder(saved);
        socketIO.emitToRoom("BOH", "order:new", orderPayload);
        socketIO.emitToRoom("MANAGER", "order:new", orderPayload);

        return saved;
    }

    // Generate the next sequential receipt number (00001, 00002, etc.)
    public String generateNextReceiptNumber() {
        String max = orderRepo.findMaxReceiptNumber();
        int next = Integer.parseInt(max) + 1;
        return String.format("%05d", next);
    }

    // Update order status (handles inventory, sales records, and socket events)
    @Transactional
    public Order updateStatus(int orderId, OrderStatus newStatus, Integer estimatedWait,
                              String paymentMethod, String receiptNumber, String cardLast4,
                              int actorId, String actorRole, String ip) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found."));

        List<OrderStatus> allowed = STATUS_TRANSITIONS.getOrDefault(order.getStatus(), List.of());
        if (!allowed.contains(newStatus)) {
            String allowedStr = allowed.isEmpty() ? "none"
                    : allowed.stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Cannot move order from " + order.getStatus() + " to " + newStatus +
                    ". Allowed next states: " + allowedStr + ".");
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.DELAYED && estimatedWait != null) {
            order.setEstimatedWait(estimatedWait);
        }
        if (newStatus != OrderStatus.DELAYED) {
            order.setEstimatedWait(null);
        }

        order = orderRepo.save(order);

        // Decrement inventory when order moves to IN_PROGRESS
        if (newStatus == OrderStatus.IN_PROGRESS) {
            decrementInventory(order);
        }

        // Audit-log cancellations
        if (newStatus == OrderStatus.CANCELLED) {
            securityLog.logEvent(actorId, "ORDER_CANCELLED",
                    "Order " + order.getId() + " (table " + order.getTableNumber() +
                    ") cancelled by " + actorRole, ip);
        }

        // Auto-create SalesRecord when order reaches COMPLETED
        if (newStatus == OrderStatus.COMPLETED) {
            if (receiptNumber != null && !receiptNumber.isBlank()) {
                order.setReceiptNumber(receiptNumber);
            } else {
                order.setReceiptNumber(generateNextReceiptNumber());
            }
            order = orderRepo.save(order);
            createSalesRecord(order, paymentMethod, cardLast4);
        }

        // Re-fetch to get full nested data
        Order updated = orderRepo.findById(orderId).orElse(order);

        // ── Socket events based on new status ───────────────────────────────
        Map<String, Object> orderPayload = serializeOrder(updated);
        int submittedById = updated.getSubmittedById();

        if (newStatus == OrderStatus.IN_PROGRESS) {
            socketIO.emitToRoom("user:" + submittedById, "order:acknowledged",
                    Map.of("order", orderPayload));
        } else if (newStatus == OrderStatus.DELAYED) {
            Map<String, Object> delayPayload = new LinkedHashMap<>();
            delayPayload.put("order", orderPayload);
            delayPayload.put("estimatedWait", updated.getEstimatedWait());
            socketIO.emitToRoom("user:" + submittedById, "order:delayed", delayPayload);
        } else if (newStatus == OrderStatus.CANCELLED) {
            socketIO.emitToRoom("user:" + submittedById, "order:cancelled",
                    Map.of("order", orderPayload));
        }

        // Always emit statusChanged to BOH + MANAGER
        socketIO.emitToRoom("BOH", "order:statusChanged", orderPayload);
        socketIO.emitToRoom("MANAGER", "order:statusChanged", orderPayload);

        return updated;
    }

    // Add a note to an order and notify the FOH user
    @Transactional
    public Order updateNote(int orderId, String note) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found."));

        String trimmed = note.trim();
        order.setBohNote(trimmed.substring(0, Math.min(trimmed.length(), 300)));
        Order updated = orderRepo.save(order);

        // ── Socket: order:note → submitting FOH user ────────────────────────
        Map<String, Object> notePayload = new LinkedHashMap<>();
        notePayload.put("orderId", updated.getId());
        notePayload.put("tableNumber", updated.getTableNumber());
        notePayload.put("note", updated.getBohNote());
        socketIO.emitToRoom("user:" + updated.getSubmittedById(), "order:note", notePayload);

        return updated;
    }

    // Decrement inventory when order is accepted, mark items unavailable if stock runs out
    private void decrementInventory(Order order) {
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());

        // Group deductions by inventory item id
        Map<Integer, Integer> deductions = new HashMap<>();
        for (OrderItem oi : items) {
            MenuItem mi = oi.getMenuItem();
            if (mi != null && mi.getInventoryItem() != null) {
                int invId = mi.getInventoryItem().getId();
                deductions.merge(invId, oi.getQuantity(), Integer::sum);
            }
        }

        List<Map<String, Object>> updatedInventory = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : deductions.entrySet()) {
            Inventory inv = inventoryRepo.findById(entry.getKey()).orElse(null);
            if (inv == null) continue;

            BigDecimal newQty = inv.getQuantity().subtract(BigDecimal.valueOf(entry.getValue()));
            // Clamp to 0
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                newQty = BigDecimal.ZERO;
            }
            inv.setQuantity(newQty);
            inventoryRepo.save(inv);

            updatedInventory.add(serializeInventory(inv));

            // Auto-mark linked menu items unavailable when stock hits 0
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                List<MenuItem> linked = menuItemRepo.findByInventoryItemId(inv.getId());
                for (MenuItem m : linked) {
                    if (m.getAvailabilityStatus() == AvailabilityStatus.AVAILABLE) {
                        m.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
                        menuItemRepo.save(m);

                        // ── Socket: item:unavailable → all roles ────────────
                        Map<String, Object> payload = Map.of(
                                "menuItemId", m.getId(),
                                "name", m.getName(),
                                "availabilityStatus", "UNAVAILABLE"
                        );
                        socketIO.emitToAllRoles("item:unavailable", payload);
                    }
                }
            }
        }

        // ── Socket: inventory:updated → BOH + MANAGER ───────────────────────
        if (!updatedInventory.isEmpty()) {
            socketIO.emitToBohAndManager("inventory:updated", updatedInventory);
        }
    }

    // Create a sales record when order is completed (skips if already exists)
    private void createSalesRecord(Order order, String paymentMethod, String cardLast4) {
        // Check if already exists (upsert behavior)
        if (salesRecordRepo.findByOrderId(order.getId()).isPresent()) {
            return;
        }

        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem oi : items) {
            if (oi.getMenuItem() != null) {
                totalAmount = totalAmount.add(
                        oi.getMenuItem().getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
            }
        }

        SalesRecord sr = new SalesRecord();
        sr.setOrder(order);
        sr.setTotalAmount(totalAmount.setScale(2, java.math.RoundingMode.HALF_UP));
        sr.setPaymentMethod(paymentMethod != null ? paymentMethod : "CASH");
        if (cardLast4 != null && !cardLast4.isBlank()) {
            sr.setCardLast4(cardLast4);
        }
        salesRecordRepo.save(sr);
    }

    // --- Helpers to convert objects to Maps for socket payloads ---

    // Convert an Order to a Map for JSON
    private Map<String, Object> serializeOrder(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("tableNumber", o.getTableNumber());
        m.put("submittedById", o.getSubmittedById());
        m.put("status", o.getStatus().name());
        m.put("estimatedWait", o.getEstimatedWait());
        m.put("bohNote", o.getBohNote());
        m.put("receiptNumber", o.getReceiptNumber());
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);

        // orderItems with menuItem
        if (o.getOrderItems() != null) {
            List<Map<String, Object>> ois = o.getOrderItems().stream().map(oi -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", oi.getId());
                item.put("orderId", oi.getOrderId());
                item.put("menuItemId", oi.getMenuItemId());
                item.put("quantity", oi.getQuantity());
                item.put("specialInstructions", oi.getSpecialInstructions());
                item.put("prepStatus", oi.getPrepStatus());
                if (oi.getMenuItem() != null) {
                    item.put("menuItem", serializeMenuItem(oi.getMenuItem()));
                }
                return item;
            }).collect(Collectors.toList());
            m.put("orderItems", ois);
        }

        // submittedBy (id + name only)
        if (o.getSubmittedBy() != null) {
            m.put("submittedBy", Map.of("id", o.getSubmittedBy().getId(), "name", o.getSubmittedBy().getName()));
        }

        // salesRecord (completedAt only when nested in order)
        if (o.getSalesRecord() != null) {
            Map<String, Object> sr = new LinkedHashMap<>();
            sr.put("completedAt", o.getSalesRecord().getCompletedAt() != null
                    ? o.getSalesRecord().getCompletedAt().toString() : null);
            m.put("salesRecord", sr);
        } else {
            m.put("salesRecord", null);
        }

        return m;
    }

    private Map<String, Object> serializeMenuItem(MenuItem mi) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mi.getId());
        m.put("name", mi.getName());
        m.put("category", mi.getCategory());
        m.put("price", mi.getPrice());
        m.put("availabilityStatus", mi.getAvailabilityStatus().name());
        m.put("inventoryItemId", mi.getInventoryItemId());
        return m;
    }

    public static Map<String, Object> serializeInventory(Inventory inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("itemName", inv.getItemName());
        m.put("quantity", inv.getQuantity().doubleValue());
        m.put("unit", inv.getUnit());
        m.put("lowStockThreshold", inv.getLowStockThreshold().doubleValue());
        m.put("restockAmount", inv.getRestockAmount().doubleValue());
        return m;
    }
}
