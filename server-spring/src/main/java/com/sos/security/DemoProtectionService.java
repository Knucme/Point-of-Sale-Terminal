package com.sos.security;

import com.sos.repository.MenuItemRepository;
import com.sos.repository.OrderRepository;
import com.sos.repository.UserRepository;
import com.sos.model.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Caps the number of rows in key tables to protect the public demo database
 * from abuse. Each cap is configurable via application properties.
 */
@Service
public class DemoProtectionService {

    private final int maxUsers;
    private final int maxMenuItems;
    private final int maxActiveOrders;

    private final UserRepository userRepo;
    private final MenuItemRepository menuItemRepo;
    private final OrderRepository orderRepo;

    public DemoProtectionService(
            @Value("${demo.max-users:10}") int maxUsers,
            @Value("${demo.max-menu-items:50}") int maxMenuItems,
            @Value("${demo.max-active-orders:30}") int maxActiveOrders,
            UserRepository userRepo,
            MenuItemRepository menuItemRepo,
            OrderRepository orderRepo) {
        this.maxUsers = maxUsers;
        this.maxMenuItems = maxMenuItems;
        this.maxActiveOrders = maxActiveOrders;
        this.userRepo = userRepo;
        this.menuItemRepo = menuItemRepo;
        this.orderRepo = orderRepo;
    }

    /** @return null if OK, or an error message string if the cap is reached */
    public String checkUserCap() {
        long count = userRepo.count();
        if (count >= maxUsers) {
            return "Demo limit reached: maximum " + maxUsers + " user accounts allowed. "
                    + "Delete an existing account before creating a new one.";
        }
        return null;
    }

    /** @return null if OK, or an error message string if the cap is reached */
    public String checkMenuItemCap() {
        long count = menuItemRepo.count();
        if (count >= maxMenuItems) {
            return "Demo limit reached: maximum " + maxMenuItems + " menu items allowed. "
                    + "Delete an existing item before adding a new one.";
        }
        return null;
    }

    /** @return null if OK, or an error message string if the cap is reached */
    public String checkActiveOrderCap() {
        // Count orders that are NOT completed or cancelled (i.e. still active)
        long active = orderRepo.countByStatusIn(List.of(
                OrderStatus.PENDING,
                OrderStatus.IN_PROGRESS,
                OrderStatus.DELAYED));
        if (active >= maxActiveOrders) {
            return "Demo limit reached: maximum " + maxActiveOrders + " active orders allowed. "
                    + "Complete or cancel existing orders before submitting new ones.";
        }
        return null;
    }
}
