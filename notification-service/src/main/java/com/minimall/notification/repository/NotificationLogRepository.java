package com.minimall.notification.repository;

import com.minimall.notification.domain.NotificationLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationLogRepository
        extends JpaRepository<NotificationLog, Long>, JpaSpecificationExecutor<NotificationLog> {

    Optional<NotificationLog> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
