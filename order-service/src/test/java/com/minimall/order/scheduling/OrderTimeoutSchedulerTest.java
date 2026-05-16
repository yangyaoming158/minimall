package com.minimall.order.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.minimall.order.config.OrderTimeoutProperties;
import com.minimall.order.service.OrderTimeoutCancellationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderTimeoutSchedulerTest {

    private final OrderTimeoutCancellationService cancellationService = mock(OrderTimeoutCancellationService.class);
    private final OrderTimeoutProperties properties = new OrderTimeoutProperties();
    private final OrderTimeoutScheduler scheduler = new OrderTimeoutScheduler(cancellationService, properties);

    @Test
    void invokesCancellationServiceWithConfiguredBatchSize() {
        properties.setBatchSize(25);
        given(cancellationService.cancelExpiredPendingOrders(any(LocalDateTime.class), eq(25)))
                .willReturn(3);

        scheduler.cancelExpiredPendingOrders();

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cancellationService).cancelExpiredPendingOrders(nowCaptor.capture(), eq(25));
        assertThat(nowCaptor.getValue()).isNotNull();
    }

    @Test
    void disabledSchedulerDoesNotInvokeCancellationService() {
        properties.setEnabled(false);

        scheduler.cancelExpiredPendingOrders();

        verifyNoInteractions(cancellationService);
    }
}
