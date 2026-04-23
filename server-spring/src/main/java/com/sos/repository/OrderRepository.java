package com.sos.repository;

import com.sos.model.Order;
import com.sos.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Query("SELECT COALESCE(MAX(o.receiptNumber), '00000') FROM Order o WHERE o.receiptNumber IS NOT NULL")
    String findMaxReceiptNumber();
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByStatusIn(List<OrderStatus> statuses);
    List<Order> findBySubmittedById(Integer userId);
    List<Order> findByCreatedAtBetween(Instant from, Instant to);
    long countByStatusIn(List<OrderStatus> statuses);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
    List<Order> findBySubmittedByIdOrderByCreatedAtDesc(Integer userId);
    long countByStatusNot(OrderStatus status);
}
