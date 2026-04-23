package com.sos.repository;

import com.sos.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    List<OrderItem> findByOrderId(Integer orderId);
    List<OrderItem> findByMenuItemIdIn(List<Integer> menuItemIds);
    boolean existsByMenuItemId(Integer menuItemId);
}
