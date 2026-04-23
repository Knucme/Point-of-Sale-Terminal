package com.sos.repository;

import com.sos.model.MenuItem;
import com.sos.model.AvailabilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {
    List<MenuItem> findByAvailabilityStatus(AvailabilityStatus status);
    Optional<MenuItem> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<MenuItem> findByInventoryItemId(Integer inventoryItemId);
    Optional<MenuItem> findByNameIgnoreCaseAndAvailabilityStatus(String name, AvailabilityStatus status);
    List<MenuItem> findByIdIn(List<Integer> ids);
    List<MenuItem> findAllByOrderByCategoryAscNameAsc();
}
