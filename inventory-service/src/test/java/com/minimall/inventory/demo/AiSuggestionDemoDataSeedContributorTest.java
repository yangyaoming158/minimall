package com.minimall.inventory.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai_suggestion_demo_data_seed;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class AiSuggestionDemoDataSeedContributorTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CoreDemoDataSeedContributor coreContributor;
    private AiSuggestionDemoDataSeedContributor contributor;

    @BeforeEach
    void setUp() {
        createSchema();
        coreContributor = new CoreDemoDataSeedContributor(jdbcTemplate);
        contributor = new AiSuggestionDemoDataSeedContributor(jdbcTemplate);
    }

    @Test
    void seedCreatesReviewWorkflowDemoRows() {
        coreContributor.seed();
        contributor.seed();

        assertThat(countSuggestions()).isEqualTo(4);
        assertThat(countSuggestionItems()).isEqualTo(4);
        assertThat(countInboundOrders()).isEqualTo(2);
        assertThat(countInboundItems()).isEqualTo(2);
        assertThat(countAppliedInboundRecords()).isEqualTo(1);

        assertThat(queryInt(
                "select count(*) from ai_operation_suggestion where status = ?",
                "PENDING_REVIEW"))
                .isEqualTo(1);
        assertThat(queryInt(
                "select count(*) from ai_operation_suggestion where status = ?",
                "REJECTED"))
                .isEqualTo(1);
        assertThat(queryInt(
                "select count(*) from ai_operation_suggestion where status = ?",
                "CONVERTED_TO_DRAFT"))
                .isEqualTo(1);
        assertThat(queryInt(
                "select count(*) from ai_operation_suggestion where status = ?",
                "APPLIED"))
                .isEqualTo(1);

        assertThat(queryString(
                "select linked_inbound_no from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-DRAFT"))
                .isEqualTo("PH3-AI-INB-DRAFT");
        assertThat(queryString(
                "select status from inbound_order where inbound_no = ?",
                "PH3-AI-INB-DRAFT"))
                .isEqualTo("DRAFT");
        assertThat(queryString(
                "select status from inbound_order where inbound_no = ?",
                "PH3-AI-INB-APPLIED"))
                .isEqualTo("APPLIED");
        assertThat(queryString(
                "select reviewed_by_admin_username from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-REJECTED"))
                .isEqualTo(AiSuggestionDemoDataSeedContributor.DEMO_ADMIN_USERNAME);
        assertThat(queryString(
                "select model_provider from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-PENDING"))
                .isEqualTo("MOCK");
        assertThat(queryString(
                "select validation_status from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-PENDING"))
                .isEqualTo("VALID");
        assertThat(queryString(
                "select input_snapshot_json from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-PENDING"))
                .contains("PH3-AI-LOW-TEA");
    }

    @Test
    void repeatSeedDoesNotDuplicateRows() {
        coreContributor.seed();

        contributor.seed();
        contributor.seed();

        assertThat(countSuggestions()).isEqualTo(4);
        assertThat(countSuggestionItems()).isEqualTo(4);
        assertThat(countInboundOrders()).isEqualTo(2);
        assertThat(countInboundItems()).isEqualTo(2);
        assertThat(countAppliedInboundRecords()).isEqualTo(1);
    }

    @Test
    void repeatSeedRestoresDeterministicReviewValues() {
        coreContributor.seed();
        contributor.seed();
        jdbcTemplate.update("""
                update ai_operation_suggestion
                   set status = ?, reason = ?, model_provider = ?
                 where suggestion_no = ?
                """,
                "REJECTED",
                "changed",
                "changed",
                "PH3-AI-SUG-PENDING");
        jdbcTemplate.update("""
                update ai_operation_suggestion_item
                   set suggested_quantity = ?, risk_level = ?
                 where suggestion_no = ?
                   and product_id = ?
                """,
                1,
                "LOW",
                "PH3-AI-SUG-DRAFT",
                "PH3-AI-LOW-TEA");
        jdbcTemplate.update("""
                update inbound_order
                   set status = ?, confirm_request_id = ?
                 where inbound_no = ?
                """,
                "CANCELLED",
                "changed",
                "PH3-AI-INB-APPLIED");
        jdbcTemplate.update("""
                update inventory_records
                   set quantity = ?, status = ?
                 where source_type = ?
                   and request_id = ?
                   and product_id = ?
                """,
                1,
                "FAILED",
                "INBOUND_ORDER",
                "PH3-AI-DEMO-CONFIRM-APPLIED",
                "PH3-AI-STABLE-CUP");

        contributor.seed();

        assertThat(queryString(
                "select status from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-PENDING"))
                .isEqualTo("PENDING_REVIEW");
        assertThat(queryString(
                "select model_provider from ai_operation_suggestion where suggestion_no = ?",
                "PH3-AI-SUG-PENDING"))
                .isEqualTo("MOCK");
        assertThat(queryInt(
                "select suggested_quantity from ai_operation_suggestion_item where suggestion_no = ? and product_id = ?",
                "PH3-AI-SUG-DRAFT",
                "PH3-AI-LOW-TEA"))
                .isEqualTo(20);
        assertThat(queryString(
                "select status from inbound_order where inbound_no = ?",
                "PH3-AI-INB-APPLIED"))
                .isEqualTo("APPLIED");
        assertThat(queryString(
                "select confirm_request_id from inbound_order where inbound_no = ?",
                "PH3-AI-INB-APPLIED"))
                .isEqualTo("PH3-AI-DEMO-CONFIRM-APPLIED");
        assertThat(queryInt(
                "select quantity from inventory_records where source_type = ? and request_id = ? and product_id = ?",
                "INBOUND_ORDER",
                "PH3-AI-DEMO-CONFIRM-APPLIED",
                "PH3-AI-STABLE-CUP"))
                .isEqualTo(16);
    }

    @Test
    void seedDoesNotChangeCurrentInventoryQuantities() {
        coreContributor.seed();
        int lowTeaStock = availableStock("PH3-AI-LOW-TEA");
        int hotMugStock = availableStock("PH3-AI-HOT-MUG");
        int stableCupStock = availableStock("PH3-AI-STABLE-CUP");

        contributor.seed();

        assertThat(availableStock("PH3-AI-LOW-TEA")).isEqualTo(lowTeaStock);
        assertThat(availableStock("PH3-AI-HOT-MUG")).isEqualTo(hotMugStock);
        assertThat(availableStock("PH3-AI-STABLE-CUP")).isEqualTo(stableCupStock);
    }

    private void createSchema() {
        jdbcTemplate.execute("drop table if exists ai_operation_suggestion_item");
        jdbcTemplate.execute("drop table if exists ai_operation_suggestion");
        jdbcTemplate.execute("drop table if exists inbound_order_item");
        jdbcTemplate.execute("drop table if exists inbound_order");
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
        jdbcTemplate.execute("""
                create table ai_operation_suggestion (
                  id bigint auto_increment primary key,
                  suggestion_no varchar(64) not null,
                  type varchar(64) not null,
                  status varchar(32) not null,
                  source varchar(64) not null,
                  reason varchar(1024),
                  input_snapshot_ref varchar(128),
                  input_summary varchar(4096),
                  model_provider varchar(64),
                  model_name varchar(128),
                  prompt_version varchar(64),
                  output_schema_version varchar(64),
                  validation_status varchar(32),
                  validation_error varchar(512),
                  input_snapshot_json varchar(16384),
                  validated_output_json varchar(16384),
                  raw_model_output_json varchar(16384),
                  linked_inbound_no varchar(64),
                  rejected_reason varchar(512),
                  reviewed_by_admin_user_id bigint,
                  reviewed_by_admin_username varchar(64),
                  reviewed_at timestamp,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_ai_operation_suggestion_no unique (suggestion_no)
                )
                """);
        jdbcTemplate.execute("""
                create table ai_operation_suggestion_item (
                  id bigint auto_increment primary key,
                  suggestion_no varchar(64) not null,
                  product_id varchar(64) not null,
                  product_name varchar(128),
                  available_stock int,
                  locked_stock int,
                  safety_stock int,
                  sold_quantity_last_7_days int,
                  suggested_quantity int not null,
                  risk_level varchar(32) not null,
                  reason varchar(1024),
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_ai_operation_suggestion_item_product unique (suggestion_no, product_id)
                )
                """);
        jdbcTemplate.execute("""
                create table inbound_order (
                  id bigint auto_increment primary key,
                  inbound_no varchar(64) not null,
                  status varchar(32) not null,
                  source varchar(32) not null,
                  created_by_admin_user_id bigint not null,
                  created_by_admin_username varchar(64) not null,
                  confirm_request_id varchar(128),
                  confirmed_by_admin_user_id bigint,
                  confirmed_by_admin_username varchar(64),
                  confirmed_at timestamp,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_inbound_order_inbound_no unique (inbound_no),
                  constraint uk_inbound_order_confirm_request_id unique (confirm_request_id)
                )
                """);
        jdbcTemplate.execute("""
                create table inbound_order_item (
                  id bigint auto_increment primary key,
                  inbound_no varchar(64) not null,
                  product_id varchar(64) not null,
                  quantity int not null,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  constraint uk_inbound_order_item_inbound_product unique (inbound_no, product_id)
                )
                """);
    }

    private int countSuggestions() {
        return queryInt("select count(*) from ai_operation_suggestion where suggestion_no like ?", "PH3-AI-SUG-%");
    }

    private int countSuggestionItems() {
        return queryInt("select count(*) from ai_operation_suggestion_item where suggestion_no like ?", "PH3-AI-SUG-%");
    }

    private int countInboundOrders() {
        return queryInt("select count(*) from inbound_order where inbound_no like ?", "PH3-AI-INB-%");
    }

    private int countInboundItems() {
        return queryInt("select count(*) from inbound_order_item where inbound_no like ?", "PH3-AI-INB-%");
    }

    private int countAppliedInboundRecords() {
        return queryInt(
                "select count(*) from inventory_records where source_type = ? and request_id like ?",
                "INBOUND_ORDER",
                "PH3-AI-DEMO-%");
    }

    private int availableStock(String productId) {
        return queryInt("select available_stock from inventory where product_id = ?", productId);
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }
}
