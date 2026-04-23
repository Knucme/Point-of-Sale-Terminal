package com.sos.repository;

import com.sos.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {
    Optional<Inventory> findByItemNameIgnoreCase(String itemName);
    boolean existsByItemNameIgnoreCase(String itemName);
}
