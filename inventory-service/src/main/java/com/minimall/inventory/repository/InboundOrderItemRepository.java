package com.minimall.inventory.repository;

import com.minimall.inventory.domain.InboundOrderItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItem, Long> {

    List<InboundOrderItem> findByInboundNoOrderByIdAsc(String inboundNo);

    List<InboundOrderItem> findByInboundNoIn(Collection<String> inboundNos);
}
