package com.minimall.order.repository;

import java.math.BigDecimal;

public interface ProductSalesAggregation {

    String getProductId();

    Long getQuantitySold();

    Long getOrderCount();

    BigDecimal getTotalAmount();
}
