package com.minimall.order.repository;

import com.minimall.order.domain.OrderEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    Optional<OrderEvent> findByEventId(String eventId);
}
