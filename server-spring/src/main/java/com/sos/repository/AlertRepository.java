package com.sos.repository;

import com.sos.model.Alert;
import com.sos.model.RecipientScope;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Integer> {
    List<Alert> findByRecipientScope(RecipientScope scope);
    List<Alert> findByTimestampAfterOrderByTimestampAsc(Instant since);
}
