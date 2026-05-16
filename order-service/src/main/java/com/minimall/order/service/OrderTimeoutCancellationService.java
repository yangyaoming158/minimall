package com.minimall.order.service;

import com.minimall.order.client.inventory.InventoryClient;
import com.minimall.order.client.inventory.InventoryDeductRequest;
import com.minimall.order.domain.Order;
import com.minimall.order.domain.OrderStatus;
import com.minimall.order.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTimeoutCancellationService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public OrderTimeoutCancellationService(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public int cancelExpiredPendingOrders(LocalDateTime now, int batchSize) {
        List<Order> expiredOrders = orderRepository.findExpiredOrders(
                OrderStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, Math.max(1, batchSize)));

        int cancelledCount = 0;
        for (Order order : expiredOrders) {
            int updated = orderRepository.updateStatusIfCurrent(
                    order.getId(),
                    OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CANCELLED,
                    now);
            if (updated == 0) {
                continue;
            }

            inventoryClient.release(new InventoryDeductRequest(
                    order.getOrderNo(),
                    order.getProductId(),
                    order.getQuantity()));
            cancelledCount++;
        }
        return cancelledCount;
    }
}
