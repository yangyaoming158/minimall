package com.minimall.notification.repository;

import com.minimall.notification.domain.NotificationLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
