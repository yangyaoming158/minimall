# Phase 2.5 — AI Inventory Assistant: Boundaries & Backlog

- Date: 2026-05-31
- TaskMaster: tag `phase2-admin-platform`, task 15.1
- Status: forward-looking record. **Nothing in this document is implemented in Phase 2.**
- Authoritative plan: the official `.taskmaster/docs/phase2-5-ai-inventory-readiness-prd.txt`
  (Phase 2.5 readiness) and `.taskmaster/docs/phase3-ai-inventory-assistant-prd.txt`
  (Phase 3 LLM assistant). This doc is the task-15.1 summary of the seams Phase 2 left;
  where it overlaps those PRDs, the PRDs win.

Phase 2 deliberately left *seams* for a future AI inventory (replenishment)
assistant without shipping any AI behaviour or UI. This document records exactly
what those seams are, what is intentionally **out of scope** for Phase 2, and the
backlog for a later Phase 2.5.

## 1. What Phase 2 already reserves (seams, not features)

These exist in the codebase today but carry **no Phase 2 behaviour** — they are
enum members / fields / layout space reserved so a later phase can plug in
without a schema or contract break.

| Seam | Where | Phase 2 status |
| --- | --- | --- |
| `InventoryRecordSourceType.AI_SUGGESTION` | inventory-service domain enum | Defined; never written by any Phase 2 path |
| `InventoryRecordSourceType.INBOUND_ORDER` | inventory-service domain enum | Defined; no inbound-order flow in Phase 2 |
| `AdminAuditSourceType.AI_SUGGESTION` / `INVENTORY_ADJUSTMENT` / `INBOUND_ORDER` | common-core audit enum | Defined; Phase 2 admin writes use `ADMIN_MANUAL` / `INVENTORY_ADJUSTMENT` |
| `AdminAuditAction.AI_SUGGESTION_CREATE` / `_APPLY` / `_REJECT` | common-core audit enum | Defined; never emitted in Phase 2 |
| `AdminAuditAction.INBOUND_ORDER_CREATE` / `_CONFIRM` | common-core audit enum | Defined; no inbound flow in Phase 2 |
| `AdminAuditResourceType.AI_SUGGESTION` / `INBOUND_ORDER` | common-core audit enum | Defined; never referenced by Phase 2 writes |
| `AdminInventoryResponse.safetyStock` + derived `lowStock` | inventory-service DTO | Surfaced in admin list/detail so AI replenishment analysis can read stock health without DB access |
| Inventory records timeline | admin-frontend `InventoriesView` records drawer | Renders changeType/quantity/reason/requestId/sourceType/referenceNo/operator; drawer header leaves room for a later "AI 补货建议" entry — **no AI UI rendered** |
| Audit traceability fields | admin-frontend `AuditLogsView` | Surfaces `sourceType` / `referenceNo` / `requestId` so admin-driven (and later AI-driven) inventory changes are traceable |

## 2. Out of scope for Phase 2 (hard boundaries)

Per the PRD and the locked admin API contract, Phase 2 does **not** include any of:

1. An AI replenishment/suggestion engine or model integration of any kind.
2. AI suggestion UI — no suggestion list, drawer, banner, or apply/reject buttons.
3. Inbound-order (purchase/restock) workflow — no create/confirm endpoints or UI,
   despite the reserved `INBOUND_ORDER*` enum members.
4. New admin write paths beyond product CRUD/status and inventory initialize/adjust.
5. Any endpoint emitting `AI_SUGGESTION_*` audit actions or writing
   `inventory_records` with `sourceType = AI_SUGGESTION`.
6. Schema changes for suggestions (no `ai_suggestion` table, no suggestion columns).

The reserved enums above must remain **inert** until Phase 2.5 implements them; a
Phase 2 reviewer seeing `AI_SUGGESTION` in an enum should expect zero references
from production code paths.

## 3. Phase 2.5 backlog (proposed, not committed)

When/if an AI inventory assistant is taken up, a likely slice order:

1. **Suggestion model + service** — generate replenishment suggestions from
   `availableStock` vs `safetyStock`/`lowStock` and historical demand
   (`order_events` / product sales aggregation already exists in order-service).
2. **`ai_suggestion` persistence** — store suggestion, status (PENDING/APPLIED/REJECTED),
   target product, proposed delta, rationale.
3. **Apply / reject endpoints** (admin-only, gateway-routed, ADMIN-enforced):
   - Apply → reuse the existing idempotent inventory adjust path, writing
     `inventory_records` with `sourceType = AI_SUGGESTION` + `referenceNo` = suggestion id,
     and an audit log with `action = AI_SUGGESTION_APPLY`, `sourceType = AI_SUGGESTION`.
   - Reject → audit `AI_SUGGESTION_REJECT`, no stock change.
   - Create → audit `AI_SUGGESTION_CREATE`.
4. **Admin UI** — a suggestions entry/drawer in `InventoriesView` (the reserved
   header slot), surfacing rationale and an apply/reject action; the records
   timeline and audit page already render the resulting `AI_SUGGESTION` source.
5. **Optional inbound-order flow** — if restock POs are needed, implement the
   reserved `INBOUND_ORDER*` actions/sources symmetrically.
6. **Editable safety stock** (independent of the AI assistant; surfaced during the
   Phase 2 smoke test) — Phase 2 only sets `safetyStock` at initialization, and
   initialization conflicts on an already-existing inventory, so existing rows are
   stuck at `safetyStock = 0` with no update path. A Phase 2.5 admin endpoint
   (e.g. `PATCH /api/admin/inventories/{productId}/safety-stock`, or extending the
   adjust request with an optional `safetyStock`) should let an admin set/raise the
   threshold, writing an `admin_operation_logs` audit record. This is what lets the
   replenishment/low-stock signals in §1 be meaningful for inventory not created via
   admin initialization.

## 4. Guardrails carried forward

Anything built in Phase 2.5 must still honour the Phase 2 invariants:

- Browser → gateway only; no service ports, no `/internal/**`, no spoofed trusted headers.
- ADMIN enforced downstream; `ApiResponse` / `PageResponse`; enum-backed status fields.
- Inventory writes idempotent by `requestId`; available stock never negative.
- Admin write operations write both `inventory_records` and `admin_operation_logs`.
- Phase 1 customer purchase flow must keep working unchanged.
