package com.sos.repository;

import com.sos.model.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalesRecordRepository extends JpaRepository<SalesRecord, Integer> {
    Optional<SalesRecord> findByOrderId(Integer orderId);
    List<SalesRecord> findByCompletedAtBetween(Instant from, Instant to);
    List<SalesRecord> findByCompletedAtGreaterThanEqual(Instant from);
    List<SalesRecord> findByCompletedAtGreaterThanEqualOrderByCompletedAtDesc(Instant from);
    List<SalesRecord> findAllByOrderByCompletedAtDesc();
    long countByCompletedAtGreaterThanEqual(Instant from);
}
