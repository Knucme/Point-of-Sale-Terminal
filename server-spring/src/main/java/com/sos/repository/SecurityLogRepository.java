package com.sos.repository;

import com.sos.model.SecurityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityLogRepository extends JpaRepository<SecurityLog, Integer> {
    Page<SecurityLog> findAllByOrderByTimestampDesc(Pageable pageable);
    Page<SecurityLog> findByEventOrderByTimestampDesc(String event, Pageable pageable);
    List<SecurityLog> findByUserId(Integer userId);
}
