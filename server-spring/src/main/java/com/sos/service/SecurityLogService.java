package com.sos.service;

import com.sos.model.SecurityLog;
import com.sos.repository.SecurityLogRepository;
import com.sos.repository.UserRepository;
import org.springframework.stereotype.Service;

// Saves security events (login attempts, order cancellations, etc.) to the database.
@Service
public class SecurityLogService {

    private final SecurityLogRepository logRepo;
    private final UserRepository userRepo;

    public SecurityLogService(SecurityLogRepository logRepo, UserRepository userRepo) {
        this.logRepo = logRepo;
        this.userRepo = userRepo;
    }

    // Log a security event (userId can be null for failed logins)
    public void logEvent(Integer userId, String event, String details, String ipAddress) {
        SecurityLog log = new SecurityLog();
        log.setEvent(event);
        log.setDetails(details);
        log.setIpAddress(ipAddress);

        if (userId != null) {
            userRepo.findById(userId).ifPresent(log::setUser);
        }

        logRepo.save(log);
    }
}
