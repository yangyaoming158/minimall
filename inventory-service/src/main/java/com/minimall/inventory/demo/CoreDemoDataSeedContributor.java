package com.minimall.inventory.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class CoreDemoDataSeedContributor implements DemoDataSeedContributor {

    static final String DEMO_USERNAME = "phase3-demo-user";
    private static final String DEMO_PASSWORD_HASH = "DEMO_DATA_USER_NOT_FOR_LOGIN";

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct(
                    "PH3-AI-LOW-TEA",
                    "Phase 3 Low Stock Tea Set",
                    "Low-stock demo SKU for replenishment analysis.",
                    "https://example.invalid/minimall/phase3-low-tea.png",
                    new BigDecimal("129.00"),
                    4,
                    1,
                    12),
            new DemoProduct(
                    "PH3-AI-HOT-MUG",
                    "Phase 3 Hot Product Mug",
                    "High-sales demo SKU for hot product analysis.",
                    "https://example.invalid/minimall/phase3-hot-mug.png",
                    new BigDecimal("39.90"),
                    42,
                    3,
                    10),
            new DemoProduct(
                    "PH3-AI-STABLE-CUP",
                    "Phase 3 Stable Stock Cup",
                    "Healthy inventory demo SKU for comparison.",
                    "https://example.invalid/minimall/phase3-stable-cup.png",
                    new BigDecimal("59.00"),
                    96,
                    0,
                    20));

    private static final List<DemoOrder> ORDERS = List.of(
            new DemoOrder("PH3-AI-ORD-HOT-001", "PH3-AI-HOT-MUG", 6, 1),
            new DemoOrder("PH3-AI-ORD-HOT-002", "PH3-AI-HOT-MUG", 7, 2),
            new DemoOrder("PH3-AI-ORD-HOT-003", "PH3-AI-HOT-MUG", 5, 3),
            new DemoOrder("PH3-AI-ORD-LOW-001", "PH3-AI-LOW-TEA", 2, 2),
            new DemoOrder("PH3-AI-ORD-LOW-002", "PH3-AI-LOW-TEA", 2, 4),
            new DemoOrder("PH3-AI-ORD-STABLE-001", "PH3-AI-STABLE-CUP", 2, 5));

    private final JdbcTemplate jdbcTemplate;

    public CoreDemoDataSeedContributor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void seed() {
        LocalDateTime seedTime = LocalDateTime.now().withNano(0);
        Long userId = seedUser();
        PRODUCTS.forEach(this::seedProduct);
        PRODUCTS.forEach(this::seedInventory);
        ORDERS.forEach(order -> seedOrder(order, userId, seedTime));
        ORDERS.forEach(order -> seedPayment(order, seedTime));
        ORDERS.forEach(order -> seedInventoryRecord(order, seedTime));
    }

    private Long seedUser() {
        Long existingId = findUserId();
        if (existingId != null) {
            jdbcTemplate.update("""
                    update users
                       set password_hash = ?,
                           email = ?,
                           phone = ?,
                           status = ?,
                           role = ?,
                           updated_at = ?
                     where username = ?
                    """,
                    DEMO_PASSWORD_HASH,
                    "phase3-demo@example.local",
                    "13000000000",
                    "ACTIVE",
                    "USER",
                    timestamp(LocalDateTime.now().withNano(0)),
                    DEMO_USERNAME);
            return existingId;
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update("""
                insert into users (
                    username, password_hash, email, phone, status, role, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DEMO_USERNAME,
                DEMO_PASSWORD_HASH,
                "phase3-demo@example.local",
                "13000000000",
                "ACTIVE",
                "USER",
                timestamp(now),
                timestamp(now));
        return findUserId();
    }

    private Long findUserId() {
        List<Long> ids = jdbcTemplate.query(
                "select id from users where username = ?",
                (rs, rowNum) -> rs.getLong("id"),
                DEMO_USERNAME);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void seedProduct(DemoProduct product) {
        if (exists("select count(*) from products where product_id = ?", product.productId())) {
            jdbcTemplate.update("""
                    update products
                       set name = ?,
                           description = ?,
                           image_url = ?,
                           price = ?,
                           status = ?,
                           updated_at = ?
                     where product_id = ?
                    """,
                    product.name(),
                    product.description(),
                    product.imageUrl(),
                    product.price(),
                    "ON_SHELF",
                    timestamp(LocalDateTime.now().withNano(0)),
                    product.productId());
            return;
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update("""
                insert into products (
                    product_id, name, description, image_url, price, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                product.productId(),
                product.name(),
                product.description(),
                product.imageUrl(),
                product.price(),
                "ON_SHELF",
                timestamp(now),
                timestamp(now));
    }

    private void seedInventory(DemoProduct product) {
        if (exists("select count(*) from inventory where product_id = ?", product.productId())) {
            jdbcTemplate.update("""
                    update inventory
                       set available_stock = ?,
                           locked_stock = ?,
                           safety_stock = ?,
                           status = ?,
                           version = 0,
                           updated_at = ?
                     where product_id = ?
                    """,
                    product.availableStock(),
                    product.lockedStock(),
                    product.safetyStock(),
                    "ACTIVE",
                    timestamp(LocalDateTime.now().withNano(0)),
                    product.productId());
            return;
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update("""
                insert into inventory (
                    product_id, available_stock, locked_stock, safety_stock, status, version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                product.productId(),
                product.availableStock(),
                product.lockedStock(),
                product.safetyStock(),
                "ACTIVE",
                0L,
                timestamp(now),
                timestamp(now));
    }

    private void seedOrder(DemoOrder order, Long userId, LocalDateTime seedTime) {
        DemoProduct product = product(order.productId());
        LocalDateTime createdAt = seedTime.minusDays(order.daysAgo()).minusHours(order.daysAgo());
        LocalDateTime paidAt = createdAt.plusMinutes(8);
        BigDecimal totalAmount = product.price().multiply(BigDecimal.valueOf(order.quantity()));
        String idempotencyKey = "idem-" + order.orderNo();

        if (exists("select count(*) from orders where order_no = ?", order.orderNo())) {
            jdbcTemplate.update("""
                    update orders
                       set user_id = ?,
                           username = ?,
                           product_id = ?,
                           product_name = ?,
                           quantity = ?,
                           unit_price = ?,
                           total_amount = ?,
                           status = ?,
                           idempotency_key = ?,
                           expire_at = ?,
                           paid_at = ?,
                           closed_at = null,
                           created_at = ?,
                           updated_at = ?
                     where order_no = ?
                    """,
                    userId,
                    DEMO_USERNAME,
                    product.productId(),
                    product.name(),
                    order.quantity(),
                    product.price(),
                    totalAmount,
                    "PAID",
                    idempotencyKey,
                    timestamp(createdAt.plusMinutes(15)),
                    timestamp(paidAt),
                    timestamp(createdAt),
                    timestamp(paidAt),
                    order.orderNo());
            return;
        }

        jdbcTemplate.update("""
                insert into orders (
                    order_no, user_id, username, product_id, product_name, quantity, unit_price,
                    total_amount, status, idempotency_key, expire_at, paid_at, closed_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.orderNo(),
                userId,
                DEMO_USERNAME,
                product.productId(),
                product.name(),
                order.quantity(),
                product.price(),
                totalAmount,
                "PAID",
                idempotencyKey,
                timestamp(createdAt.plusMinutes(15)),
                timestamp(paidAt),
                null,
                timestamp(createdAt),
                timestamp(paidAt));
    }

    private void seedPayment(DemoOrder order, LocalDateTime seedTime) {
        DemoProduct product = product(order.productId());
        LocalDateTime paidAt = seedTime.minusDays(order.daysAgo()).minusHours(order.daysAgo()).plusMinutes(8);
        BigDecimal amount = product.price().multiply(BigDecimal.valueOf(order.quantity()));
        String paymentNo = order.orderNo().replace("ORD", "PAY");
        String idempotencyKey = "pay-" + order.orderNo();

        if (exists("select count(*) from payments where order_no = ?", order.orderNo())) {
            jdbcTemplate.update("""
                    update payments
                       set payment_no = ?,
                           amount = ?,
                           channel = ?,
                           status = ?,
                           idempotency_key = ?,
                           paid_at = ?,
                           updated_at = ?
                     where order_no = ?
                    """,
                    paymentNo,
                    amount,
                    "MOCK",
                    "SUCCESS",
                    idempotencyKey,
                    timestamp(paidAt),
                    timestamp(paidAt),
                    order.orderNo());
            return;
        }

        jdbcTemplate.update("""
                insert into payments (
                    payment_no, order_no, amount, channel, status, idempotency_key, paid_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                paymentNo,
                order.orderNo(),
                amount,
                "MOCK",
                "SUCCESS",
                idempotencyKey,
                timestamp(paidAt),
                timestamp(paidAt),
                timestamp(paidAt));
    }

    private void seedInventoryRecord(DemoOrder order, LocalDateTime seedTime) {
        LocalDateTime createdAt = seedTime.minusDays(order.daysAgo()).minusHours(order.daysAgo()).plusMinutes(9);
        if (exists(
                "select count(*) from inventory_records where source_type = ? and request_id = ? and product_id = ?",
                "ORDER_DEDUCT",
                order.orderNo(),
                order.productId())) {
            jdbcTemplate.update("""
                    update inventory_records
                       set order_no = ?,
                           change_type = ?,
                           quantity = ?,
                           reason = ?,
                           admin_user_id = null,
                           admin_username = null,
                           reference_no = ?,
                           status = ?,
                           created_at = ?,
                           updated_at = ?
                     where source_type = ?
                       and request_id = ?
                       and product_id = ?
                    """,
                    order.orderNo(),
                    "DEDUCT",
                    order.quantity(),
                    "Phase 3 demo paid order stock deduction",
                    order.orderNo(),
                    "SUCCESS",
                    timestamp(createdAt),
                    timestamp(createdAt),
                    "ORDER_DEDUCT",
                    order.orderNo(),
                    order.productId());
            return;
        }

        jdbcTemplate.update("""
                insert into inventory_records (
                    product_id, order_no, request_id, change_type, source_type, quantity, reason,
                    admin_user_id, admin_username, reference_no, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.productId(),
                order.orderNo(),
                order.orderNo(),
                "DEDUCT",
                "ORDER_DEDUCT",
                order.quantity(),
                "Phase 3 demo paid order stock deduction",
                null,
                null,
                order.orderNo(),
                "SUCCESS",
                timestamp(createdAt),
                timestamp(createdAt));
    }

    private DemoProduct product(String productId) {
        return PRODUCTS.stream()
                .filter(product -> product.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown demo product " + productId));
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private record DemoProduct(
            String productId,
            String name,
            String description,
            String imageUrl,
            BigDecimal price,
            int availableStock,
            int lockedStock,
            int safetyStock) {
    }

    private record DemoOrder(String orderNo, String productId, int quantity, int daysAgo) {
    }
}
