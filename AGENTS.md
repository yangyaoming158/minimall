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
4. Mark the task/subtask done only after verification.
5. Append progress to `docs/dev-log.md`.

## Hard Rules
- All REST APIs must return `ApiResponse`.
- Business errors must use `BusinessException`.
- Status fields must use enums.
- Order, inventory, payment, and MQ consumers must be idempotent.
- Do not hardcode database passwords, JWT secrets, hostnames, or ports.
- Use configuration files and environment variables.
- If the same error appears twice, stop and report the blocker.