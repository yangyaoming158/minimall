# AGENTS.md

## Project
MiniMall Order is a Java 17 + Spring Boot 3 + Spring Cloud microservice order system.

## Task Workflow
Use TaskMaster as the source of truth.

Before implementing:
1. Run `task-master next`.
2. Run `task-master show <id>`.
3. If the task is large, run `task-master expand --id=<id> --num=<n>`.
4. Explain the implementation plan.
5. Wait for confirmation unless explicitly told to proceed.

During implementation:
- Implement only the current task or subtask.
- Do not modify unrelated modules.
- Do not introduce components outside the current task.
- Do not manually edit `.taskmaster/tasks/tasks.json` unless TaskMaster CLI/MCP is unavailable.
- Use TaskMaster commands whenever possible.

After implementation:
1. Run the task's testStrategy.
2. Run `mvn clean package -DskipTests` when relevant.
3. Summarize changed files.
4. For every completed task/subtask, list every modified, added, or deleted file name in the final response so the user can review the exact scope.
5. Mark the task/subtask done only after verification.
6. Append progress to `docs/dev-log.md`.

## Hard Rules
- All REST APIs must return `ApiResponse`.
- Business errors must use `BusinessException`.
- Status fields must use enums.
- Order, inventory, payment, and MQ consumers must be idempotent.
- Do not hardcode database passwords, JWT secrets, hostnames, or ports.
- Use configuration files and environment variables.
- Frontend-ready acceptance criteria only require stable backend API contracts for future frontend/admin integration; do not implement frontend pages, admin consoles, UI assets, or frontend build tooling in the current backend task tree. Real frontend development must wait for the user's next PRD.
- If the same error appears twice, stop and report the blocker.

## Version Management
- Keep `main` as the stable integration branch; do not commit accumulated task work directly to `main` unless the user explicitly requests it.
- Create a `codex/` branch before committing a task group, checkpoint, or feature batch, for example `codex/task-8-inventory` or `codex/checkpoint-current-work`.
- Prefer one commit per completed TaskMaster task or subtask after its testStrategy passes; if the working tree already contains multiple completed tasks, create a checkpoint commit on a `codex/` branch.
- Commit `.taskmaster/tasks/tasks.json` and `docs/dev-log.md` together with the code changes for the task they describe.
