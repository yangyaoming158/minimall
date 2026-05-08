package com.minimall.order.service;

import com.minimall.common.auth.context.UserContext;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.common.core.exception.ErrorCode;
import com.minimall.order.dto.CreateOrderRequest;
import com.minimall.order.dto.CreateOrderResponse;
import org.springframework.stereotype.Service;

@Service
public class OrderCommandService {

    public CreateOrderResponse create(CreateOrderRequest request, UserContext userContext) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Order creation is not ready");
    }
}
