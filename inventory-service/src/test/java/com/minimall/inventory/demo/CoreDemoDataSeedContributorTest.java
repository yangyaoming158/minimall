package com.minimall.inventory.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:core_demo_data_seed;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class CoreDemoDataSeedContributorTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CoreDemoDataSeedContributor contributor;

    @BeforeEach
    void setUp() {
        createSchema();
        contributor = new CoreDemoDataSeedContributor(jdbcTemplate);
    }

    @Test
    void seedCreatesCorePhase3DemoRows() {
        contributor.seed();

        assertThat(count("users", "username", CoreDemoDataSeedContributor.DEMO_USERNAME)).isEqualTo(1);
        assertThat(countDemoProducts()).isEqualTo(3);
        assertThat(countDemoInventory()).isEqualTo(3);
        assertThat(countDemoOrders()).isEqualTo(6);
        assertThat(countDemoPayments()).isEqualTo(6);
        assertThat(countDemoInventoryRecords()).isEqualTo(6);

        assertThat(queryInt(
                "select available_stock from inventory where product_id = ?",
                "PH3-AI-LOW-TEA"))
                .isEqualTo(4);
        assertThat(queryInt(
                "select safety_stock from inventory where product_id = ?",
                "PH3-AI-LOW-TEA"))
                .isEqualTo(12);
        assertThat(queryInt(
                "select coalesce(sum(quantity), 0) from orders where product_id = ? and status = ?",
                "PH3-AI-HOT-MUG",
                "PAID"))
                .isEqualTo(18);
        assertThat(queryBigDecimal(
                "select coalesce(sum(total_amount), 0) from orders where product_id = ? and status = ?",
                "PH3-AI-HOT-MUG",
                "PAID"))
                .isEqualByComparingTo("718.20");
    }

    @Test
    void repeatSeedDoesNotDuplicateRows() {
        contributor.seed();
        contributor.seed();

        assertThat(count("users", "username", CoreDemoDataSeedContributor.DEMO_USERNAME)).isEqualTo(1);
        assertThat(countDemoProducts()).isEqualTo(3);
        assertThat(countDemoInventory()).isEqualTo(3);
        assertThat(countDemoOrders()).isEqualTo(6);
        assertThat(countDemoPayments()).isEqualTo(6);
        assertThat(countDemoInventoryRecords()).isEqualTo(6);
    }

    @Test
    void repeatSeedRestoresDeterministicProductAndInventoryValues() {
        contributor.seed();
        jdbcTemplate.update("""
                update products
                   set name = ?, price = ?, status = ?
                 where product_id = ?
                """, "changed", new BigDecimal("1.00"), "OFF_SHELF", "PH3-AI-HOT-MUG");
        jdbcTemplate.update("""
                update inventory
                   set available_stock = ?, locked_stock = ?, safety_stock = ?, status = ?, version = ?
                 where product_id = ?
                """, 999, 99, 1, "INACTIVE", 42L, "PH3-AI-HOT-MUG");

        contributor.seed();

        assertThat(queryString(
                "select name from products where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isEqualTo("Phase 3 Hot Product Mug");
        assertThat(queryBigDecimal(
                "select price from products where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isEqualByComparingTo("39.90");
        assertThat(queryString(
                "select status from products where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isEqualTo("ON_SHELF");
        assertThat(queryInt(
                "select available_stock from inventory where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isEqualTo(42);
        assertThat(queryInt(
                "select locked_stock from inventory where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isEqualTo(3);
        assertThat(queryInt(
                "select safety_stock from inventory where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isEqualTo(10);
        assertThat(queryInt(
                "select version from inventory where product_id = ?",
                "PH3-AI-HOT-MUG"))
                .isZero();
    }

    private void createSchema() {
        jdbcTemplate.execute("drop table if exists inventory_records");
        jdbcTemplate.execute("drop table if exists payments");
        jdbcTemplate.execute("drop table if exists orders");
        jdbcTemplate.execute("drop table if exists inventory");
        jdbcTemplate.execute("drop table if exists products");
        jdbcTemplate.execute("drop table if exists users");

        jdbcTemplate.execute("""
                create table users (
                  id bigint auto_increment primary key,
                  username varchar(64) not null,
                  password_hash varchar(255) not null,
                  email varchar(128),
                  phone varchar(32),
                  status varchar(32) not null,
                  role varchar(32) not null,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_users_username unique (username)
                )
                """);
        jdbcTemplate.execute("""
                create table products (
                  id bigint auto_increment primary key,
                  product_id varchar(64) not null,
                  name varchar(128) not null,
                  description varchar(1024),
                  image_url varchar(512),
                  price decimal(12, 2) not null,
                  status varchar(32) not null,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_products_product_id unique (product_id)
                )
                """);
        jdbcTemplate.execute("""
                create table inventory (
                  id bigint auto_increment primary key,
                  product_id varchar(64) not null,
                  available_stock int not null,
                  locked_stock int not null,
                  safety_stock int not null,
                  status varchar(32) not null,
                  version bigint not null,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_inventory_product_id unique (product_id)
                )
                """);
        jdbcTemplate.execute("""
                create table orders (
                  id bigint auto_increment primary key,
                  order_no varchar(64) not null,
                  user_id bigint not null,
                  username varchar(64),
                  product_id varchar(64) not null,
                  product_name varchar(128) not null,
                  quantity int not null,
                  unit_price decimal(12, 2) not null,
                  total_amount decimal(12, 2) not null,
                  status varchar(32) not null,
                  idempotency_key varchar(128),
                  expire_at timestamp,
                  paid_at timestamp,
                  closed_at timestamp,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_orders_order_no unique (order_no),
                  constraint uk_orders_user_id_idempotency_key unique (user_id, idempotency_key)
                )
                """);
        jdbcTemplate.execute("""
                create table payments (
                  id bigint auto_increment primary key,
                  payment_no varchar(64) not null,
                  order_no varchar(64) not null,
                  amount decimal(12, 2) not null,
                  channel varchar(32) not null,
                  status varchar(32) not null,
                  idempotency_key varchar(128),
                  paid_at timestamp,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_payments_payment_no unique (payment_no),
                  constraint uk_payments_order_no unique (order_no),
                  constraint uk_payments_idempotency_key unique (idempotency_key)
                )
                """);
        jdbcTemplate.execute("""
                create table inventory_records (
                  id bigint auto_increment primary key,
                  product_id varchar(64) not null,
                  order_no varchar(64),
                  request_id varchar(128),
                  change_type varchar(32) not null,
                  source_type varchar(32) not null,
                  quantity int not null,
                  reason varchar(512),
                  admin_user_id bigint,
                  admin_username varchar(64),
                  reference_no varchar(128),
                  status varchar(32) not null,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_inventory_records_order_change unique (order_no, change_type),
                  constraint uk_inventory_records_source_request_product unique (source_type, request_id, product_id)
                )
                """);
    }

    private int countDemoProducts() {
        return queryInt("select count(*) from products where product_id like ?", "PH3-AI-%");
    }

    private int countDemoInventory() {
        return queryInt("select count(*) from inventory where product_id like ?", "PH3-AI-%");
    }

    private int countDemoOrders() {
        return queryInt("select count(*) from orders where order_no like ?", "PH3-AI-ORD-%");
    }

    private int countDemoPayments() {
        return queryInt("select count(*) from payments where payment_no like ?", "PH3-AI-PAY-%");
    }

    private int countDemoInventoryRecords() {
        return queryInt("select count(*) from inventory_records where request_id like ?", "PH3-AI-ORD-%");
    }

    private int count(String table, String column, String value) {
        return queryInt("select count(*) from " + table + " where " + column + " = ?", value);
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private BigDecimal queryBigDecimal(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }
}
