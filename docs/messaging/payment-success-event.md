# Payment Success Event Contract

## Scope

This document defines the RabbitMQ topology and JSON payload used when `payment-service` publishes a successful payment event for downstream `order-service` and `notification-service` consumers.

The source of truth in code is:

- `PaymentEventNames` in `common-core/src/main/java/com/minimall/common/core/event/payment/PaymentEventNames.java`
- `PaymentSuccessEvent` in `common-core/src/main/java/com/minimall/common/core/event/payment/PaymentSuccessEvent.java`
- `PaymentRabbitTopologyAutoConfiguration` in `common-core/src/main/java/com/minimall/common/core/config/PaymentRabbitTopologyAutoConfiguration.java`

## RabbitMQ Topology

| Type | Name | Notes |
| --- | --- | --- |
| Exchange | `minimall.payment.exchange` | Durable direct exchange for payment-domain events. |
| Routing key | `payment.success` | Used only for payment success events. |
| Queue | `minimall.order.payment-success.queue` | Durable queue consumed by `order-service`. |
| Queue | `minimall.notification.payment-success.queue` | Durable queue consumed by `notification-service`. |

Bindings:

| Queue | Exchange | Routing key |
| --- | --- | --- |
| `minimall.order.payment-success.queue` | `minimall.payment.exchange` | `payment.success` |
| `minimall.notification.payment-success.queue` | `minimall.payment.exchange` | `payment.success` |

`PaymentRabbitTopologyAutoConfiguration` exposes a `paymentRabbitTopologyDeclarables` bean containing the exchange, both queues, and both bindings. Services that include `common-core` and Spring AMQP can let Spring/RabbitMQ auto-declare the topology on startup. A service may provide its own bean named `paymentRabbitTopologyDeclarables` to override the shared declaration, but that should be rare and must preserve the same public names.

## Event Payload

Message body content type is JSON. Producers and consumers should use `PaymentSuccessEvent` from `common-core` rather than redefining the DTO locally.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `eventId` | string | Yes | Globally unique message id. Consumers must use this as the idempotency key. |
| `orderNo` | string | Yes | Business order number affected by the payment. |
| `paymentNo` | string | Yes | Business payment number. |
| `amount` | decimal | Yes | Paid amount. Preserve decimal precision; do not serialize as a string unless a future version explicitly changes the contract. |
| `paidAt` | string | Yes | Payment completion time as an ISO-8601 instant, for example `2026-05-11T10:15:30Z`. |
| `version` | integer | No | Event schema version. Missing value means version `1`. Current version is `1`. |

Example:

```json
{
  "eventId": "payment-success-20260511-000001",
  "orderNo": "ORD20260511000001",
  "paymentNo": "PAY20260511000001",
  "amount": 199.90,
  "paidAt": "2026-05-11T10:15:30Z",
  "version": 1
}
```

## Producer Rules

`payment-service` is the only publisher for `payment.success`.

- Publish to exchange `minimall.payment.exchange` with routing key `payment.success`.
- Generate one stable `eventId` for each successful payment event.
- Include `orderNo`, `paymentNo`, `amount`, and `paidAt` from committed payment state.
- Publish only after the payment is committed as successful.
- Retrying publish must reuse the same `eventId` for the same logical payment success.
- Do not publish secrets, internal database ids, stack traces, or customer PII in this event.

## Consumer Rules

Consumers must be idempotent because RabbitMQ delivery is at-least-once.

`order-service`:

- Consume from `minimall.order.payment-success.queue`.
- Use `eventId` to deduplicate event handling.
- Treat repeated events for the same `eventId` as successful no-ops.
- Transition only the matching `orderNo` from pending payment state to paid state.
- Ignore or reject events whose `paymentNo` or `amount` conflicts with the persisted order/payment state.

`notification-service`:

- Consume from `minimall.notification.payment-success.queue`.
- Use `eventId` to deduplicate notification log writes and sends.
- Treat repeated events for the same `eventId` as successful no-ops.
- Use `orderNo`, `paymentNo`, `amount`, and `paidAt` only for notification content and audit records.

## Versioning

Version `1` contains exactly the fields listed above. Consumers must accept a missing `version` as `1` for compatibility with the DTO default. Future versions may add optional fields, but must not change the meaning or type of existing version `1` fields.

Breaking changes require a new routing key and a separate consumer migration plan. Do not reuse `payment.success` for an incompatible payload.

## Verification

Current automated coverage:

- `PaymentEventNamesTest` verifies stable exchange, routing key, and queue names.
- `PaymentSuccessEventTest` verifies JSON serialization/deserialization, required `eventId`, missing-version compatibility, ISO `paidAt`, and amount precision.
- `PaymentRabbitTopologyAutoConfigurationTest` verifies durable exchange/queues, binding routing keys, and custom bean backoff.

Recommended local checks:

```bash
mvn -pl common-core test
mvn clean package -DskipTests
```
