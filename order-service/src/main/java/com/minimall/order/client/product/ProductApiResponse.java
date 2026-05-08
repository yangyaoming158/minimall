package com.minimall.order.client.product;

record ProductApiResponse(
        boolean success,
        String code,
        String message,
        ProductSnapshot data) {
}
