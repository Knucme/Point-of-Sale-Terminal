package com.sos.controller;

import com.sos.dto.*;
import com.sos.model.AvailabilityStatus;
import com.sos.model.Inventory;
import com.sos.model.MenuItem;
import com.sos.repository.InventoryRepository;
import com.sos.repository.MenuItemRepository;
import com.sos.repository.OrderItemRepository;
import com.sos.security.JwtPrincipal;
import com.sos.service.OrderService;
import com.sos.service.SecurityLogService;
import com.sos.service.SocketIOService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

// Inventory endpoints: list, create, update, delete, restock
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryRepository inventoryRepo;
    private final MenuItemRepository menuItemRepo;
    private final OrderItemRepository orderItemRepo;
    private final SecurityLogService securityLog;
    private final SocketIOService socketIO;

    public InventoryController(InventoryRepository inventoryRepo,
                               MenuItemRepository menuItemRepo,
                               OrderItemRepository orderItemRepo,
                               SecurityLogService securityLog,
                               SocketIOService socketIO) {
        this.inventoryRepo = inventoryRepo;
        this.menuItemRepo = menuItemRepo;
        this.orderItemRepo = orderItemRepo;
        this.securityLog = securityLog;
        this.socketIO = socketIO;
    }

    // ── GET /api/inventory ──────────────────────────────────────────────────
    // Manager only. Returns all inventory items with a low-stock flag.
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getAll() {
        try {
            List<Inventory> items = inventoryRepo.findAll(Sort.by("itemName").ascending());
            // Annotate each item with isLowStock (matches Node.js response shape)
            List<Map<String, Object>> annotated = items.stream().map(item -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", item.getId());
                map.put("itemName", item.getItemName());
                map.put("quantity", item.getQuantity());
                map.put("unit", item.getUnit());
                map.put("lowStockThreshold", item.getLowStockThreshold());
                map.put("restockAmount", item.getRestockAmount());
                map.put("isLowStock", item.getQuantity().compareTo(item.getLowStockThreshold()) <= 0);
                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(annotated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch inventory."));
        }
    }

    // ── GET /api/inventory/:id ──────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getById(@PathVariable int id) {
        try {
            Inventory item = inventoryRepo.findById(id).orElse(null);
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Inventory item not found."));
            }
            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch inventory item."));
        }
    }

    // ── POST /api/inventory ─────────────────────────────────────────────────
    // Manager creates a new inventory item.
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> create(@Valid @RequestBody CreateInventoryRequest req,
                                    @AuthenticationPrincipal JwtPrincipal principal,
                                    HttpServletRequest httpReq) {
        if (req.getQuantity().signum() < 0) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Inventory quantity cannot be negative."));
        }

        try {
            // Case-insensitive duplicate check
            var existing = inventoryRepo.findByItemNameIgnoreCase(req.getItemName().trim());
            if (existing.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(
                                "An inventory item named \"" + existing.get().getItemName() + "\" already exists."));
            }

            Inventory item = new Inventory();
            item.setItemName(req.getItemName().trim());
            item.setQuantity(req.getQuantity());
            item.setUnit(req.getUnit().trim());
            item.setLowStockThreshold(req.getLowStockThreshold());
            if (req.getRestockAmount() != null) {
                item.setRestockAmount(req.getRestockAmount());
            }

            item = inventoryRepo.save(item);

            securityLog.logEvent(principal.getId(), "INVENTORY_CREATE",
                    "Created inventory " + item.getId() + " \"" + item.getItemName() + "\" qty=" + item.getQuantity(),
                    httpReq.getRemoteAddr());

            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create inventory item."));
        }
    }

    // ── PATCH /api/inventory/:id ────────────────────────────────────────────
    // Manager updates quantity and/or low-stock threshold.
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> update(@PathVariable int id,
                                    @RequestBody UpdateInventoryRequest req,
                                    @AuthenticationPrincipal JwtPrincipal principal,
                                    HttpServletRequest httpReq) {
        if (req.getQuantity() != null && req.getQuantity().signum() < 0) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Inventory quantity cannot go below 0."));
        }

        try {
            Inventory item = inventoryRepo.findById(id).orElse(null);
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Inventory item not found."));
            }

            List<String> changed = new ArrayList<>();
            if (req.getItemName() != null) {
                // Check for case-insensitive duplicates (excluding self)
                var dup = inventoryRepo.findByItemNameIgnoreCase(req.getItemName().trim());
                if (dup.isPresent() && !dup.get().getId().equals(id)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErrorResponse(
                                    "An inventory item named \"" + dup.get().getItemName() + "\" already exists."));
                }
                item.setItemName(req.getItemName().trim());
                changed.add("itemName");
            }
            if (req.getUnit() != null) { item.setUnit(req.getUnit().trim()); changed.add("unit"); }
            if (req.getQuantity() != null) { item.setQuantity(req.getQuantity()); changed.add("quantity"); }
            if (req.getLowStockThreshold() != null) { item.setLowStockThreshold(req.getLowStockThreshold()); changed.add("lowStockThreshold"); }
            if (req.getRestockAmount() != null) { item.setRestockAmount(req.getRestockAmount()); changed.add("restockAmount"); }

            item = inventoryRepo.save(item);

            securityLog.logEvent(principal.getId(), "INVENTORY_UPDATE",
                    "Updated inventory " + item.getId() + " \"" + item.getItemName() + "\" fields: " + String.join(", ", changed),
                    httpReq.getRemoteAddr());

            // Return with isLowStock annotation like the Node route
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", item.getId());
            result.put("itemName", item.getItemName());
            result.put("quantity", item.getQuantity());
            result.put("unit", item.getUnit());
            result.put("lowStockThreshold", item.getLowStockThreshold());
            result.put("restockAmount", item.getRestockAmount());
            result.put("isLowStock", item.getQuantity().compareTo(item.getLowStockThreshold()) <= 0);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update inventory item."));
        }
    }

    // ── POST /api/inventory/:id/restock ─────────────────────────────────────
    // BOH or Manager: adds restockAmount to quantity; re-enables linked menu items if out of stock.
    @PostMapping("/{id}/restock")
    @PreAuthorize("hasAnyRole('BOH', 'MANAGER')")
    @Transactional
    public ResponseEntity<?> restock(@PathVariable int id,
                                     @AuthenticationPrincipal JwtPrincipal principal,
                                     HttpServletRequest httpReq) {
        try {
            Inventory inv = inventoryRepo.findById(id).orElse(null);
            if (inv == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Inventory item not found."));
            }

            boolean wasEmpty = inv.getQuantity().compareTo(BigDecimal.ZERO) <= 0;
            BigDecimal restockAmt = inv.getRestockAmount();

            inv.setQuantity(inv.getQuantity().add(restockAmt));
            inv = inventoryRepo.save(inv);

            // Re-enable linked menu items if they were out of stock
            if (wasEmpty) {
                List<MenuItem> linked = menuItemRepo.findByInventoryItemId(inv.getId());
                for (MenuItem m : linked) {
                    if (m.getAvailabilityStatus() == AvailabilityStatus.UNAVAILABLE) {
                        m.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
                        menuItemRepo.save(m);

                        // Socket: item:available → all roles
                        Map<String, Object> avail = Map.of(
                                "menuItemId", m.getId(), "name", m.getName(),
                                "availabilityStatus", "AVAILABLE");
                        socketIO.emitToAllRoles("item:available", avail);
                    }
                }
            }

            // Socket: inventory:updated → BOH + MANAGER
            socketIO.emitToBohAndManager("inventory:updated",
                    List.of(OrderService.serializeInventory(inv)));

            securityLog.logEvent(principal.getId(), "INVENTORY_RESTOCK",
                    "Restocked " + inv.getId() + " \"" + inv.getItemName() + "\" by " + restockAmt + " (wasEmpty=" + wasEmpty + ")",
                    httpReq.getRemoteAddr());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", inv.getId());
            result.put("itemName", inv.getItemName());
            result.put("quantity", inv.getQuantity());
            result.put("unit", inv.getUnit());
            result.put("lowStockThreshold", inv.getLowStockThreshold());
            result.put("restockAmount", inv.getRestockAmount());
            result.put("wasEmpty", wasEmpty);
            result.put("restocked", restockAmt);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to restock item."));
        }
    }

    /** Serialize a MenuItem with its inventoryItem for socket payload. */
    private Map<String, Object> serializeMenuForSocket(MenuItem m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("name", m.getName());
        map.put("category", m.getCategory());
        map.put("price", m.getPrice());
        map.put("availabilityStatus", m.getAvailabilityStatus().name());
        map.put("inventoryItemId", m.getInventoryItemId());
        if (m.getInventoryItem() != null) {
            Map<String, Object> inv = new LinkedHashMap<>();
            inv.put("id", m.getInventoryItem().getId());
            inv.put("itemName", m.getInventoryItem().getItemName());
            inv.put("quantity", m.getInventoryItem().getQuantity());
            inv.put("unit", m.getInventoryItem().getUnit());
            inv.put("lowStockThreshold", m.getInventoryItem().getLowStockThreshold());
            inv.put("restockAmount", m.getInventoryItem().getRestockAmount());
            map.put("inventoryItem", inv);
        } else {
            map.put("inventoryItem", null);
        }
        return map;
    }

    // ── DELETE /api/inventory/:id ────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable int id,
                                    @AuthenticationPrincipal JwtPrincipal principal,
                                    HttpServletRequest httpReq) {
        try {
            Inventory inv = inventoryRepo.findById(id).orElse(null);
            if (inv == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Inventory item not found."));
            }

            // Handle linked menu items
            List<MenuItem> linked = menuItemRepo.findByInventoryItemId(id);
            if (!linked.isEmpty()) {
                List<Integer> menuIds = linked.stream().map(MenuItem::getId).collect(Collectors.toList());

                // Find which menu items have order history
                Set<Integer> usedIds = new HashSet<>();
                for (Integer menuId : menuIds) {
                    if (orderItemRepo.existsByMenuItemId(menuId)) {
                        usedIds.add(menuId);
                    }
                }

                List<Integer> safeToDelete = menuIds.stream().filter(mid -> !usedIds.contains(mid)).collect(Collectors.toList());
                List<Integer> mustKeep = menuIds.stream().filter(usedIds::contains).collect(Collectors.toList());

                // Unlink menu items that have order history (can't delete due to FK)
                for (Integer keepId : mustKeep) {
                    MenuItem m = menuItemRepo.findById(keepId).orElse(null);
                    if (m != null) {
                        m.setInventoryItem(null);
                        m.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
                        menuItemRepo.save(m);
                        // Socket: menu:updated for unlinked items
                        MenuItem updated = menuItemRepo.findById(keepId).orElse(m);
                        socketIO.emitToAllRoles("menu:updated", serializeMenuForSocket(updated));
                    }
                }
                // Delete menu items with no order history
                for (Integer delId : safeToDelete) {
                    menuItemRepo.deleteById(delId);
                    // Socket: menu:deleted for removed items
                    socketIO.emitToAllRoles("menu:deleted", Map.of("id", delId));
                }
            }

            inventoryRepo.delete(inv);

            securityLog.logEvent(principal.getId(), "INVENTORY_DELETE",
                    "Deleted inventory " + inv.getId() + " \"" + inv.getItemName() +
                    "\" (" + linked.size() + " linked menu item(s) cleaned up)",
                    httpReq.getRemoteAddr());

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete inventory item."));
        }
    }
}
