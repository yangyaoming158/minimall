package com.minimall.payment.repository;

import com.minimall.payment.domain.Order;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findByOrderNoIn(Collection<String> orderNos);
}
