# AGENTS.md

## Project
MiniMall Order is a Java 17 + Spring Boot 3 + Spring Cloud microservice
e-commerce system: 7 services behind an api-gateway, two independent
frontends (`frontend` storefront, `admin-frontend` console), MySQL/Redis/
RabbitMQ via Docker Compose.

Current phase: Phase 3 AI Inventory Assistant.
- Active TaskMaster tag: `phase3-ai-inventory-assistant` (6/13 done; next is
  7.2, then 7.3 → 8 → 9 → 10 → 11–13).
- Authoritative docs, in priority order:
  1. docs/phase3-ai-inventory-contract.md (locked AI boundaries and APIs)
  2. .taskmaster/docs/phase3-ai-inventory-assistant-prd.txt
  3. docs/architecture-ai-review-2026-06-10.md (full-repo review: known
     issues M1–M5, P0/P1/P2 roadmap, demo gaps)
  4. CLAUDE.md (phase rules; keep AGENTS.md and CLAUDE.md consistent)

Completed task trees that must not be reopened or regressed: `master`
(backend MVP 20/20), `phase0-api-polish`, `phase1-customer-frontend`,
`phase2-admin-platform`, `phase2-5-ai-inventory-readiness`.

## Task Workflow
Use TaskMaster as the source of truth.

Before implementing:
1. Confirm the active tag is `phase3-ai-inventory-assistant`
   (`task-master tags`); never run `parse-prd`, `set-status`, `expand`, or
   implementation workflow commands against `master` or any completed tag.
2. Run `task-master next --tag=phase3-ai-inventory-assistant`.
3. Run `task-master show <id>` (set the current tag first; see CLI nuances).
4. Explicitly state whether the task will be split before implementation,
   using `是否拆分：是/否，原因：...`. If the task is large or spans unclear
   boundaries, split it (see the `expand` caveat below).
5. Explain the implementation plan.
6. Wait for confirmation unless explicitly told to proceed.

During implementation:
- Implement only the current task or subtask.
- Do not modify unrelated modules.
- Do not introduce components outside the current task.
- Do not implement future tasks early (e.g. do not start the admin AI page
  while Task 8/9 backend work is open).
- If the implementation must deviate from
  docs/phase3-ai-inventory-contract.md, update that contract document in the
  same task — contract drift is a known failure mode (see review M5).
- Do not manually edit `.taskmaster/tasks/tasks.json` unless TaskMaster
  CLI/MCP is unavailable.

TaskMaster CLI nuances (project-local CLI):
- If global `task-master` is unavailable, use
  `node node_modules/task-master-ai/dist/task-master.js COMMAND`.
- `--tag=<tag>` is accepted on `next`, `list`, `add-subtask`.
- `--tag` is NOT accepted on `show` or `set-status`. For those, ensure the
  desired tag is current first (`use-tag <tag>`, verify with `tags`), then
  run the bare command.
- `expand --id=<id> --num=<n>` has historically hung or failed in this
  WSL/Codex sandbox. If it errors or does not return promptly, fall back to
  manual `add-subtask --parent=<id>` for each subtask. Do not retry `expand`
  in a loop.

After implementation:
1. Run the task's testStrategy.
2. HARD RULE: if any backend file changed, run the full-reactor
   `mvn clean package -DskipTests` — a single-module test pass is not
   sufficient.
3. If frontend files changed, run `npm run build` (and tests) in that
   frontend, plus the gateway-contract audit.
4. Mark the task/subtask done only after verification passes.
5. Append progress to `docs/dev-log.md` (date, status, split decision,
   implemented, changed files, commands, test result, issues, next).
6. Propose a commit (message + staged file list) and STOP. Committing
   requires a fresh per-commit authorization from the user; see Version
   Management.
7. List every modified, added, or deleted file in the final response.

## Hard Rules
- All REST APIs must return `ApiResponse`; admin lists use `PageResponse<T>`.
- Business errors must use `BusinessException` with existing `ErrorCode`s.
- Status fields must use enums with stable names.
- Order, inventory, payment, and MQ consumers must be idempotent; keep the
  DB unique-constraint backstops intact.
- Do not hardcode database passwords, JWT secrets, AI API keys, model IDs,
  hostnames, or ports. Use configuration files and environment variables.
- AI boundaries (locked contract — never violate, never weaken):
  - AI code must not directly update `inventory` or `inventory_records`,
    execute SQL, call `/internal/**`, confirm inbound orders, or call the
    admin inventory adjust API.
  - Model output must pass `AiModelOutputValidator` before being returned or
    persisted; do not relax validator rules to make an output pass.
  - Suggestion statuses are only PENDING_REVIEW / CONVERTED_TO_DRAFT /
    REJECTED / APPLIED; inbound orders only DRAFT / CONFIRMED / APPLIED /
    CANCELLED.
  - Browser and AI-facing calls enter only through api-gateway under
    `/api/admin/**`; never send X-User-Id / X-Username / X-User-Role /
    X-Internal-Token from browser code.
- Known issues M1–M5 in docs/architecture-ai-review-2026-06-10.md are
  registered debt: do not fix them as drive-by changes in unrelated tasks;
  address them only via the review roadmap or explicit user approval.
- If the same error appears twice, stop and report the blocker.

## Command Reliability
- Sessions run inside WSL with project root
  `/home/oslab/projects/mini-mall-order`. Prefer direct commands from the
  project root, e.g. `node node_modules/task-master-ai/dist/task-master.js
  tags` and `mvn test`.
- Do not wrap commands in `wsl bash -lc` when already inside WSL.
- Prefer the WSL-local `node`; do not use Windows Node paths such as
  `/mnt/d/nodejs/node.exe` unless WSL-local Node is missing.
- Prefer short, predictable commands; avoid clever shell composition,
  pipelines, command substitution, nested quotes, heredocs, semicolon
  chains, and regex alternation when a simpler command or separate tool call
  is enough.
- Do not use long inline `printf`/`echo` blocks to write files; use the
  editor/patch tool for source, docs, YAML, SQL, JSON, and dev-log edits.
- Do not create temporary edit scripts for routine changes. If a temporary
  file is truly unavoidable, create it under the project root with a
  `.codex-` prefix, delete it before continuing, and verify it is gone.
- If a command fails due to shell parsing or quoting, simplify the command
  shape or switch to a patch tool; do not keep retrying variants.
- Treat the WSL NAT/localhost warning as noise only when the command exit
  code is 0.

## Version Management
- Keep `main` as the stable integration branch; do not commit task work
  directly to `main` unless the user explicitly requests it.
- Work on a branch starting with `codex/` (current:
  `codex/phase3-ai-inventory-assistant`). If on `main`, create or switch to
  a `codex/` branch first.
- COMMIT GATE: every git commit requires a fresh, explicit per-commit
  authorization from the user immediately before it. A task prompt that
  mentions committing is NOT standing consent. The agent's job ends at:
  verification passed → TaskMaster status updated → dev-log appended →
  files staged (only this task's files, including
  `.taskmaster/tasks/tasks.json` and `docs/dev-log.md` when changed) →
  commit message proposed → stop and wait for "go".
- Before proposing the commit, run `git status --short` and
  `git diff --cached --name-only` to verify the staged scope.
- If the working tree already contains multiple completed tasks, propose a
  checkpoint commit on a `codex/` branch and list the included tasks.
