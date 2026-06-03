package com.minimall.inventory.repository;

import com.minimall.inventory.domain.InboundOrder;
import com.minimall.inventory.domain.InboundOrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InboundOrderRepository
        extends JpaRepository<InboundOrder, Long>, JpaSpecificationExecutor<InboundOrder> {

    Optional<InboundOrder> findByInboundNo(String inboundNo);

    Optional<InboundOrder> findByConfirmRequestId(String confirmRequestId);

    boolean existsByInboundNo(String inboundNo);

    Page<InboundOrder> findByStatus(InboundOrderStatus status, Pageable pageable);
}
