package com.sos.config;

import com.sos.model.*;
import com.sos.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Seeds the database with demo data on startup if its empty.
// Only runs when there are no users in the DB (fresh install).
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedDatabase(UserRepository userRepo,
                                   InventoryRepository inventoryRepo,
                                   MenuItemRepository menuItemRepo,
                                   OrderRepository orderRepo,
                                   OrderItemRepository orderItemRepo) {
        return args -> {
            // Only seed if the database is empty
            if (userRepo.count() > 0) {
                log.info("Database already has data, skipping seed.");
                return;
            }

            log.info("Database is empty — seeding demo data...");

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

            // ── Inventory ──────────────────────────────────────────────────
            log.info("Creating inventory items...");
            Map<String, Inventory> inv = new HashMap<>();
            Object[][] invData = {
                {"Grilled Chicken Sandwich", 20, 3, 10},
                {"Classic Burger",           20, 3, 10},
                {"BBQ Bacon Burger",         20, 3, 10},
                {"French Fries",              2, 5, 20},  // low stock — drains to 0 when Order 1 is accepted
                {"House Salad",              15, 3, 10},
                {"Chicken Wings (6 pc)",     20, 3, 10},
                {"Mozzarella Sticks",        15, 3, 10},
                {"Draft Beer",               24, 5, 12},
                {"Soft Drink",               30, 5, 20},
                {"Chocolate Lava Cake",      12, 2,  6},
            };

            for (Object[] row : invData) {
                Inventory item = new Inventory();
                item.setItemName((String) row[0]);
                item.setQuantity(BigDecimal.valueOf((int) row[1]));
                item.setUnit("qty");
                item.setLowStockThreshold(BigDecimal.valueOf((int) row[2]));
                item.setRestockAmount(BigDecimal.valueOf((int) row[3]));
                item = inventoryRepo.save(item);
                inv.put((String) row[0], item);
            }

            // ── Menu Items ─────────────────────────────────────────────────
            log.info("Creating menu items...");
            Map<String, MenuItem> menu = new HashMap<>();
            Object[][] menuData = {
                {"Grilled Chicken Sandwich", "Mains",      12.99, "Grilled Chicken Sandwich"},
                {"Classic Burger",           "Mains",      11.99, "Classic Burger"},
                {"BBQ Bacon Burger",         "Mains",      14.99, "BBQ Bacon Burger"},
                {"French Fries",             "Sides",       4.99, "French Fries"},
                {"House Salad",              "Sides",       6.99, "House Salad"},
                {"Chicken Wings (6 pc)",     "Appetizers", 10.99, "Chicken Wings (6 pc)"},
                {"Mozzarella Sticks",        "Appetizers",  8.99, "Mozzarella Sticks"},
                {"Draft Beer",               "Drinks",      5.99, "Draft Beer"},
                {"Soft Drink",               "Drinks",      2.99, "Soft Drink"},
                {"Chocolate Lava Cake",      "Desserts",    7.99, "Chocolate Lava Cake"},
            };

            for (Object[] row : menuData) {
                MenuItem item = new MenuItem();
                item.setName((String) row[0]);
                item.setCategory((String) row[1]);
                item.setPrice(BigDecimal.valueOf((double) row[2]));
                item.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
                item.setInventoryItem(inv.get((String) row[3]));
                item = menuItemRepo.save(item);
                menu.put((String) row[0], item);
            }

            // ── Users ──────────────────────────────────────────────────────
            log.info("Creating user accounts...");

            User manager = new User();
            manager.setName("Alex Rivera");
            manager.setUsername("manager");
            manager.setPasswordHash(encoder.encode("Manager-Dev-2026"));
            manager.setRole(Role.MANAGER);
            userRepo.save(manager);

            User bohCook = new User();
            bohCook.setName("Marco Chen");
            bohCook.setUsername("boh_cook");
            bohCook.setPasswordHash(encoder.encode("BohCook-Dev-2026"));
            bohCook.setRole(Role.BOH);
            userRepo.save(bohCook);

            User fohServer = new User();
            fohServer.setName("Sofia Patel");
            fohServer.setUsername("foh_server");
            fohServer.setPasswordHash(encoder.encode("FohSvr-Dev-2026"));
            fohServer.setRole(Role.FOH);
            fohServer = userRepo.save(fohServer);

            // ── Test Orders ────────────────────────────────────────────────
            log.info("Creating test orders...");

            // Order 1 — PENDING, Table 3 (accepting this drains French Fries to 0)
            Order order1 = new Order();
            order1.setTableNumber(3);
            order1.setSubmittedBy(fohServer);
            order1.setStatus(OrderStatus.PENDING);
            order1.setCreatedAt(Instant.now().minusSeconds(4 * 60));
            List<OrderItem> items1 = new ArrayList<>();
            items1.add(makeOrderItem(order1, menu.get("Classic Burger"), 1, null));
            items1.add(makeOrderItem(order1, menu.get("French Fries"), 2, null));
            items1.add(makeOrderItem(order1, menu.get("Soft Drink"), 1, null));
            order1.setOrderItems(items1);
            orderRepo.save(order1);

            // Order 2 — PENDING, Table 7
            Order order2 = new Order();
            order2.setTableNumber(7);
            order2.setSubmittedBy(fohServer);
            order2.setStatus(OrderStatus.PENDING);
            order2.setCreatedAt(Instant.now().minusSeconds(2 * 60));
            List<OrderItem> items2 = new ArrayList<>();
            items2.add(makeOrderItem(order2, menu.get("Chicken Wings (6 pc)"), 1, null));
            items2.add(makeOrderItem(order2, menu.get("Mozzarella Sticks"), 1, "Extra marinara"));
            items2.add(makeOrderItem(order2, menu.get("Draft Beer"), 3, null));
            order2.setOrderItems(items2);
            orderRepo.save(order2);

            // Order 3 — IN_PROGRESS, Table 1
            Order order3 = new Order();
            order3.setTableNumber(1);
            order3.setSubmittedBy(fohServer);
            order3.setStatus(OrderStatus.IN_PROGRESS);
            order3.setCreatedAt(Instant.now().minusSeconds(12 * 60));
            List<OrderItem> items3 = new ArrayList<>();
            items3.add(makeOrderItem(order3, menu.get("BBQ Bacon Burger"), 1, null));
            items3.add(makeOrderItem(order3, menu.get("Grilled Chicken Sandwich"), 1, null));
            items3.add(makeOrderItem(order3, menu.get("House Salad"), 1, "No croutons"));
            order3.setOrderItems(items3);
            orderRepo.save(order3);

            // Order 4 — DELAYED, Table 5
            Order order4 = new Order();
            order4.setTableNumber(5);
            order4.setSubmittedBy(fohServer);
            order4.setStatus(OrderStatus.DELAYED);
            order4.setEstimatedWait(10);
            order4.setBohNote("Grill is backed up — 10 more minutes");
            order4.setCreatedAt(Instant.now().minusSeconds(18 * 60));
            List<OrderItem> items4 = new ArrayList<>();
            items4.add(makeOrderItem(order4, menu.get("BBQ Bacon Burger"), 2, null));
            items4.add(makeOrderItem(order4, menu.get("French Fries"), 2, null));
            items4.add(makeOrderItem(order4, menu.get("Chocolate Lava Cake"), 2, null));
            order4.setOrderItems(items4);
            orderRepo.save(order4);

            log.info("Seed complete!");
            log.info("┌────────────────────────────────────────────────────────────────┐");
            log.info("│                   Seed Account Credentials                   │");
            log.info("├──────────┬──────────────┬──────────────────┬─────────────────┤");
            log.info("│ Role     │ Username     │ Name             │ Password        │");
            log.info("├──────────┼──────────────┼──────────────────┼─────────────────┤");
            log.info("│ MANAGER  │ manager      │ Alex Rivera      │ Manager-Dev-2026│");
            log.info("│ BOH      │ boh_cook     │ Marco Chen       │ BohCook-Dev-2026│");
            log.info("│ FOH      │ foh_server   │ Sofia Patel      │ FohSvr-Dev-2026 │");
            log.info("└──────────┴──────────────┴──────────────────┴─────────────────┘");
        };
    }

    private OrderItem makeOrderItem(Order order, MenuItem menuItem, int qty, String instructions) {
        OrderItem oi = new OrderItem();
        oi.setOrder(order);
        oi.setMenuItem(menuItem);
        oi.setQuantity(qty);
        if (instructions != null) {
            oi.setSpecialInstructions(instructions);
        }
        return oi;
    }
}
