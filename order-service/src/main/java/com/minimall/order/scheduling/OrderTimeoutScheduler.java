package com.minimall.order.scheduling;

import com.minimall.order.config.OrderTimeoutProperties;
import com.minimall.order.service.OrderTimeoutCancellationService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderTimeoutCancellationService cancellationService;
    private final OrderTimeoutProperties properties;

    public OrderTimeoutScheduler(
            OrderTimeoutCancellationService cancellationService,
            OrderTimeoutProperties properties) {
        this.cancellationService = cancellationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${minimall.order.timeout.fixed-delay:60000}")
    public void cancelExpiredPendingOrders() {
        if (!properties.isEnabled()) {
            log.debug("Order timeout scheduler is disabled");
            return;
        }

        int cancelledCount = cancellationService.cancelExpiredPendingOrders(
                LocalDateTime.now(),
                properties.getBatchSize());
        if (cancelledCount > 0) {
            log.info("Cancelled {} expired pending orders", cancelledCount);
        }
    }
}
