# MiniMall Order

MiniMall Order is a full-stack mini e-commerce system: Java 17 / Spring Boot 3
/ Spring Cloud microservices behind an API gateway, two Vue 3 frontends
(customer storefront and admin operations console), and an LLM-powered AI
inventory assistant guarded by strict backend output validation. The local
runtime is Docker Compose: MySQL, Redis, RabbitMQ, Prometheus, Grafana, and
seven Spring Boot service containers.

## Project Status

All six delivery phases are complete. There is no active phase; new work
needs a new PRD or explicit direction.

| Phase | Scope | Acceptance |
| --- | --- | --- |
| Backend MVP | Microservice order system, gateway, messaging, observability | `docs/final-acceptance.md` |
| Phase 0 | API polish: response envelopes, pagination, contract cleanup | `docs/phase0-acceptance.md` |
| Phase 1 | Customer storefront (`frontend`) | `docs/phase1-frontend-acceptance.md` |
| Phase 2 | Admin platform: admin auth, products, inventories, orders, payments, notifications, audit logs | `docs/phase2-admin-acceptance.md` |
| Phase 2.5 | Stock-mutation foundation: inbound orders, AI-suggestion review lifecycle | `docs/phase2-5-ai-inventory-readiness-acceptance.md` |
| Phase 3 | AI inventory assistant: Q&A, analyses, replenishment suggestions, daily report | `docs/phase3-acceptance.md` (2026-06-11) |

## Modules

| Module | Role |
| --- | --- |
| `common-core` | Shared `ApiResponse`, business exceptions, payment event DTOs, and RabbitMQ topology. |
| `common-auth` | JWT utilities, trusted user context, internal-secret enforcement, and servlet auth propagation. |
| `api-gateway` | Sole browser-facing entrypoint: canonical route forwarding, JWT validation, trusted-header injection, CORS, rate limiting. |
| `user-service` | Registration, login, current-user lookup, admin login/identity, audit logs. |
| `product-service` | Product management APIs and Redis-backed product detail cache. |
| `inventory-service` | Inventory query/lock/release/deduct, idempotent inventory records, inbound orders, AI suggestions, and all `/api/admin/ai/**` endpoints. |
| `order-service` | Order creation, cancellation, timeout handling, payment-success consumption, sales statistics. |
| `payment-service` | Payment creation/query, idempotent payment success, payment-success event publishing. |
| `notification-service` | Payment-success notification consumer and notification log persistence. |
| `frontend` | Vue 3 customer storefront (Phase 1). Dev server on `5173`. |
| `admin-frontend` | Vue 3 + Element Plus admin operations console (Phases 2–3). Dev server on `5174`. |

## Start From Zero

Prerequisites: JDK 17, Maven, Docker with Compose v2, Node.js 18+.

Create a local environment file and replace every `change-me-*` value:

```bash
cp .env.example .env
```

Start infrastructure first:

```bash
docker compose --env-file .env up -d mysql redis rabbitmq prometheus grafana
```

Apply all database migrations in version order when the MySQL volume is new
or empty (V1 through the latest; lexical glob order is wrong for V10+):

```bash
for f in $(ls docs/sql/migrations/V*.sql | sort -V); do
  docker compose --env-file .env exec -T mysql sh -lc \
    'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < "$f"
done
```

Build the service jars and start all Java services:

```bash
mvn clean package -DskipTests
docker compose --env-file .env --profile services up -d --build
```

Check service health:

```bash
docker compose --env-file .env --profile services ps
curl --noproxy '*' -fsS http://127.0.0.1:8080/actuator/health
```

Run the frontends (each talks only to the gateway via `VITE_API_BASE_URL`,
default `http://localhost:8080`):

```bash
cd frontend && npm install && npm run dev        # customer, http://localhost:5173
cd admin-frontend && npm install && npm run dev  # admin,    http://localhost:5174
```

To create the initial ADMIN account, set the `MINIMALL_ADMIN_SEED_*`
variables in `.env` for one user-service startup, then disable them again
(see `.env.example`).

For day-to-day commands see `docs/dev-quickstart.md`.

## Runtime Entrypoints

| Component | Local URL |
| --- | --- |
| API gateway (only browser-facing API) | `http://127.0.0.1:${API_GATEWAY_PORT}` (default 8080) |
| Customer storefront (dev) | `http://localhost:5173` |
| Admin console (dev) | `http://localhost:5174` |
| RabbitMQ management | `http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT}` |
| Prometheus | `http://127.0.0.1:${PROMETHEUS_PORT}` |
| Grafana | `http://127.0.0.1:${GRAFANA_PORT}` |

Browsers must call the gateway only. Service ports (`8101`–`8106`) are not
published by Compose and are never a frontend contract; `/internal/**` is
blocked at the gateway.

Canonical API prefixes:

| Area | Gateway prefix | Owner |
| --- | --- | --- |
| Customer | `/api/users/**`, `/api/products/**`, `/api/inventories/**`, `/api/orders/**`, `/api/payments/**` | respective services |
| Admin auth & audit | `/api/admin/login`, `/api/admin/me`, `/api/admin/audit-logs/**` | user-service |
| Admin products | `/api/admin/products/**` | product-service |
| Admin inventory & AI | `/api/admin/inventories/**`, `/api/admin/inbound-orders/**`, `/api/admin/ai-suggestions/**`, `/api/admin/ai/**`, `/api/admin/operation-stats/inventory-trends` | inventory-service |
| Admin orders & stats | `/api/admin/orders/**`, `/api/admin/operation-stats/sales-by-product` | order-service |
| Admin payments | `/api/admin/payments/**` | payment-service |
| Admin notifications | `/api/admin/notifications/**` | notification-service |

All REST APIs return `ApiResponse`; admin list endpoints use
`PageResponse<T>`. The gateway strips browser-supplied `X-User-Id` /
`X-Username` / `X-User-Role` / `X-Internal-Token` and injects trusted values
after JWT validation; downstream services still verify ADMIN locally.

## AI Inventory Assistant

The admin console's `/ai-inventory` page offers inventory Q&A, low-stock
analysis, hot-product analysis, replenishment-suggestion generation, and a
daily operations report. All AI endpoints live under `/api/admin/ai/**`
(inventory-service, ADMIN-only). The locked contract is
`docs/phase3-ai-inventory-contract.md`.

Hard boundary — AI never changes stock. The only stock-affecting flow is:

```text
analysis → validated suggestion (PENDING_REVIEW) → admin review
→ convert-inbound-draft → inbound DRAFT → admin confirm (requestId-idempotent)
→ inventory transaction + records + audit → suggestion APPLIED
```

Every model output must pass `AiModelOutputValidator` before it is returned
or persisted: productId whitelist, numeric facts equal to the input snapshot,
date whitelist, and SQL / internal-path / stock-execution-claim rejection.
Validation and provider failures map to controlled `ApiResponse` errors and
persist nothing.

Providers are pluggable behind the `AiProvider` interface (DeepSeek, MiniMax,
MOCK) and configured via environment variables (consumed only by
inventory-service; the API key is not shared with other containers):

| Variable | Default | Meaning |
| --- | --- | --- |
| `AI_PROVIDER` | `MOCK` | `MOCK`, `DEEPSEEK`, or `MINIMAX` |
| `AI_MODEL` | empty | Provider model id |
| `AI_BASE_URL` | empty | Provider API base URL |
| `AI_API_KEY` | empty | Provider API key (never commit it) |
| `AI_REQUEST_TIMEOUT_MS` | `5000` | Provider request timeout |
| `AI_TEMPERATURE` | `0` | Sampling temperature |
| `AI_MODEL_STRICT_JSON` | `true` | Ask the provider for strict-JSON output |
| `AI_MOCK_ENABLED` | `true` | Allow the MOCK provider |

The default MOCK provider derives deterministic items from the input
snapshot, so the full suggestion loop is demoable offline and always passes
validation. Real providers are nondeterministic: a generation attempt can end
in a controlled validation failure (e.g. quantity above the snapshot-derived
cap) or an empty result; the UI distinguishes missing data, unsupported
question, model failure, and validation failure. Use MOCK for deterministic
demos. Real-model findings and prompt-v2 fixes are recorded in
`docs/ai-assistant-smoke-findings-2026-06-12.md`.

## Known Limitations

Deliberate trade-offs and accepted risks, from
`docs/architecture-ai-review-2026-06-10.md`. Do not "fix" these silently.

- **M1 — internal-secret legacy fallback.** When
  `minimall.auth.internal.secret` (env `MINIMALL_AUTH_INTERNAL_SECRET`) is
  unset, `UserContextFilter` trusts `X-User-*` propagation headers as-is.
  Docker Compose makes the secret mandatory (mapped from
  `MINIMALL_INTERNAL_TOKEN`), so this only affects non-Compose deployments:
  always set the secret there.
- **M2 — order-creation saga gap.** Order creation deducts (locks) inventory
  remotely before the local order insert. A crash between the two can orphan
  locked stock; there is no automatic compensation job.
- **M3 — payment-service maps the `orders` table directly** instead of
  calling order-service, a deliberate shortcut that couples it to the order
  schema.
- **M4 — real AI providers send business data to external vendors.** With
  `AI_PROVIDER=DEEPSEEK`/`MINIMAX`, inventory and sales snapshots leave the
  machine for the provider's API. Demos default to MOCK; switch knowingly.
- **lockedStock is never consumed after payment.** Payment success deducts
  available stock and the lock is a modeling simplification left by design;
  explain it, don't refactor it.

## Documentation Map

- `docs/dev-quickstart.md`: day-to-day local startup commands (Chinese).
- `docs/architecture.md`: module boundaries, request flow, data model, events.
- `docs/deployment.md`: deployment, env variables, startup order, health.
- `docs/api-gateway-contract.md`: stable gateway routes, auth, CORS, rate limits.
- `docs/phase3-ai-inventory-contract.md`: locked AI API contract (authoritative for AI boundaries).
- `docs/architecture-ai-review-2026-06-10.md`: whole-repo architecture & AI review; source of the known-limitations registry.
- `docs/ai-assistant-smoke-findings-2026-06-12.md`: real-LLM smoke findings and prompt-v2 fixes.
- `docs/phase0-acceptance.md` … `docs/phase3-acceptance.md`: per-phase acceptance gates.
- `docs/frontend-integration.md`: frontend API usage and response envelope rules.
- `frontend/README.md`: customer frontend quickstart.
- `docs/sql/README.md`: schema migration layout (V1–V12).
- `docs/messaging/payment-success-event.md`: RabbitMQ payment event topology.
- `docs/observability.md`: Prometheus and Grafana provisioning.
- `pressure/README.md`: k6 gateway pressure-test usage.
- `docs/dev-log.md`: per-task implementation log (9-field entries).

## Verification

Use these checks before handing over a local environment:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile services config --quiet
mvn clean package -DskipTests
cd frontend && npm run build
cd admin-frontend && npm run build
```

If any frontend file changed, also run the gateway-contract audit (greps both
frontends for trusted identity headers, `/internal/`, and service ports
8101–8106; all three must be clean).
