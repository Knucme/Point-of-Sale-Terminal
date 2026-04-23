package com.sos.controller;

import com.sos.dto.*;
import com.sos.model.AvailabilityStatus;
import com.sos.model.Inventory;
import com.sos.model.MenuItem;
import com.sos.repository.InventoryRepository;
import com.sos.repository.MenuItemRepository;
import com.sos.security.DemoProtectionService;
import com.sos.security.JwtPrincipal;
import com.sos.service.SecurityLogService;
import com.sos.service.SocketIOService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Menu endpoints: list items, create, update, toggle availability
@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuItemRepository menuItemRepo;
    private final InventoryRepository inventoryRepo;
    private final SecurityLogService securityLog;
    private final SocketIOService socketIO;
    private final DemoProtectionService demoProtection;

    public MenuController(MenuItemRepository menuItemRepo,
                          InventoryRepository inventoryRepo,
                          SecurityLogService securityLog,
                          SocketIOService socketIO,
                          DemoProtectionService demoProtection) {
        this.menuItemRepo = menuItemRepo;
        this.inventoryRepo = inventoryRepo;
        this.securityLog = securityLog;
        this.socketIO = socketIO;
        this.demoProtection = demoProtection;
    }

    // ── GET /api/menu ───────────────────────────────────────────────────────
    // All authenticated roles can view the menu.
    @GetMapping
    public ResponseEntity<?> getMenu() {
        try {
            List<MenuItem> items = menuItemRepo.findAllByOrderByCategoryAscNameAsc();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch menu."));
        }
    }

    // ── GET /api/menu/:id ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getMenuItem(@PathVariable int id) {
        try {
            MenuItem item = menuItemRepo.findById(id).orElse(null);
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Menu item not found."));
            }
            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch menu item."));
        }
    }

    // ── PATCH /api/menu/:id/availability ────────────────────────────────────
    // BOH can toggle item availability; Manager can too.
    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('BOH', 'MANAGER')")
    @Transactional
    public ResponseEntity<?> toggleAvailability(@PathVariable int id,
                                                @Valid @RequestBody AvailabilityRequest req) {
        String statusStr = req.getAvailabilityStatus();
        if (!"AVAILABLE".equals(statusStr) && !"UNAVAILABLE".equals(statusStr)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("availabilityStatus must be AVAILABLE or UNAVAILABLE."));
        }

        try {
            MenuItem item = menuItemRepo.findById(id).orElse(null);
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Menu item not found."));
            }
            item.setAvailabilityStatus(AvailabilityStatus.valueOf(statusStr));
            item = menuItemRepo.save(item);

            // Socket: item:available or item:unavailable → FOH + MANAGER
            String event = "UNAVAILABLE".equals(statusStr) ? "item:unavailable" : "item:available";
            Map<String, Object> payload = Map.of(
                    "menuItemId", item.getId(), "name", item.getName(), "availabilityStatus", statusStr);
            socketIO.emitToRoom("FOH", event, payload);
            socketIO.emitToRoom("MANAGER", event, payload);

            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update item availability."));
        }
    }

    // ── POST /api/menu ──────────────────────────────────────────────────────
    // Manager only: create a new menu item.
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> createMenuItem(@Valid @RequestBody CreateMenuItemRequest req,
                                            @AuthenticationPrincipal JwtPrincipal principal,
                                            HttpServletRequest httpReq) {
        if (req.getPrice().signum() < 0) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("price must be a non-negative number."));
        }

        // Demo protection: cap menu items
        String capError = demoProtection.checkMenuItemCap();
        if (capError != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse(capError));
        }

        try {
            // Case-insensitive duplicate check (ignore orphaned UNAVAILABLE items)
            var existing = menuItemRepo.findByNameIgnoreCaseAndAvailabilityStatus(
                    req.getName().trim(), AvailabilityStatus.AVAILABLE);
            if (existing.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(
                                "A menu item named \"" + existing.get().getName() + "\" already exists."));
            }

            MenuItem item = new MenuItem();
            item.setName(req.getName().trim());
            item.setCategory(req.getCategory().trim());
            item.setPrice(req.getPrice());

            if (req.getInventoryItemId() != null) {
                Inventory inv = inventoryRepo.findById(req.getInventoryItemId()).orElse(null);
                item.setInventoryItem(inv);
            }

            item = menuItemRepo.save(item);

            securityLog.logEvent(principal.getId(), "MENU_CREATE",
                    "Created menu item " + item.getId() + " \"" + item.getName() + "\" @ $" + item.getPrice(),
                    httpReq.getRemoteAddr());

            // Socket: menu:created → all roles (re-fetch with inventoryItem for full payload)
            MenuItem full = menuItemRepo.findById(item.getId()).orElse(item);
            socketIO.emitToAllRoles("menu:created", serializeMenuFull(full));

            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create menu item."));
        }
    }

    // ── PUT /api/menu/:id ───────────────────────────────────────────────────
    // Manager only: update an existing menu item.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> updateMenuItem(@PathVariable int id,
                                            @RequestBody UpdateMenuItemRequest req,
                                            @AuthenticationPrincipal JwtPrincipal principal,
                                            HttpServletRequest httpReq) {
        try {
            MenuItem item = menuItemRepo.findById(id).orElse(null);
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Menu item not found."));
            }

            java.math.BigDecimal oldPrice = item.getPrice();
            java.util.List<String> changedFields = new java.util.ArrayList<>();

            if (req.getName() != null) {
                item.setName(req.getName().trim());
                changedFields.add("name");
            }
            if (req.getCategory() != null) {
                item.setCategory(req.getCategory().trim());
                changedFields.add("category");
            }
            if (req.getPrice() != null) {
                item.setPrice(req.getPrice());
                changedFields.add("price");
            }
            if (req.getInventoryItemId() != null) {
                Inventory inv = inventoryRepo.findById(req.getInventoryItemId()).orElse(null);
                item.setInventoryItem(inv);
                changedFields.add("inventoryItemId");
            }
            if (req.getAvailabilityStatus() != null) {
                item.setAvailabilityStatus(AvailabilityStatus.valueOf(req.getAvailabilityStatus()));
                changedFields.add("availabilityStatus");
            }

            item = menuItemRepo.save(item);

            boolean priceChanged = req.getPrice() != null && oldPrice.compareTo(item.getPrice()) != 0;
            securityLog.logEvent(principal.getId(),
                    priceChanged ? "MENU_PRICE_CHANGE" : "MENU_UPDATE",
                    priceChanged
                            ? "Menu item " + item.getId() + " \"" + item.getName() + "\" price $" + oldPrice + " → $" + item.getPrice()
                            : "Updated menu item " + item.getId() + " \"" + item.getName() + "\" fields: " + String.join(", ", changedFields),
                    httpReq.getRemoteAddr());

            // Socket: menu:updated → all roles
            MenuItem full = menuItemRepo.findById(item.getId()).orElse(item);
            socketIO.emitToAllRoles("menu:updated", serializeMenuFull(full));

            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update menu item."));
        }
    }

    // ── DELETE /api/menu/:id ────────────────────────────────────────────────
    // Manager only.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<?> deleteMenuItem(@PathVariable int id,
                                            @AuthenticationPrincipal JwtPrincipal principal,
                                            HttpServletRequest httpReq) {
        try {
            MenuItem item = menuItemRepo.findById(id).orElse(null);
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Menu item not found."));
            }

            int deletedId = item.getId();
            menuItemRepo.delete(item);

            securityLog.logEvent(principal.getId(), "MENU_DELETE",
                    "Deleted menu item " + deletedId + " \"" + item.getName() + "\"",
                    httpReq.getRemoteAddr());

            // Socket: menu:deleted → all roles
            socketIO.emitToAllRoles("menu:deleted", Map.of("id", deletedId));

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete menu item."));
        }
    }

    /** Serialize a MenuItem with its inventoryItem for socket payload. */
    private Map<String, Object> serializeMenuFull(MenuItem m) {
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
}
