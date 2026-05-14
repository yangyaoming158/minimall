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
- If global task-master is unavailable, use the project-local CLI: node node_modules/task-master-ai/dist/task-master.js COMMAND, replacing COMMAND with next, show 15.2, set-status --id=15.2 --status=in-progress, etc.

After implementation:
1. Run the task's testStrategy.
2. Run `mvn clean package -DskipTests` when relevant.
3. Mark the task/subtask done only after verification passes.
4. Append progress to `docs/dev-log.md`.
5. Commit the completed task/subtask before the final response. This is mandatory unless the user explicitly says not to commit or a blocker prevents committing.
6. Summarize changed files.
7. For every completed task/subtask, list every modified, added, or deleted file name in the final response so the user can review the exact scope.

## Hard Rules
- All REST APIs must return `ApiResponse`.
- Business errors must use `BusinessException`.
- Status fields must use enums.
- Order, inventory, payment, and MQ consumers must be idempotent.
- Do not hardcode database passwords, JWT secrets, hostnames, or ports.
- Use configuration files and environment variables.
- Frontend-ready acceptance criteria only require stable backend API contracts for future frontend/admin integration; do not implement frontend pages, admin consoles, UI assets, or frontend build tooling in the current backend task tree. Real frontend development must wait for the user's next PRD.
- If the same error appears twice, stop and report the blocker.

## Command Reliability
- The local workspace is reached through PowerShell into WSL. Prefer short, predictable commands and avoid clever shell composition.
- Use this simple pattern for normal WSL commands: `wsl bash -lc "cd /home/oslab/projects/mini-mall-order && <command>"`.
- For commands that need a quoted argument, keep quoting shallow. For example, use `wsl bash -lc "cd /home/oslab/projects/mini-mall-order && git commit -m 'message text'"`.
- Do not use long inline `printf`/`echo` blocks to write files. Use `apply_patch` for source, docs, YAML, SQL, JSON, and dev-log edits.
- Avoid pipelines, command substitution, nested quotes, heredocs, semicolon chains, and regex alternation through the PowerShell-to-WSL bridge. Split them into separate simple commands instead.
- Prefer `grep -R fixedText -n <paths>` or separate fixed-string searches over complex grep patterns containing `|`, quotes, or shell metacharacters.
- Do not create temporary edit scripts for routine changes. If a temporary file is truly unavoidable, create it under the project root with a `.codex-` prefix, delete it before continuing, and verify it is gone.
- If a command fails due shell parsing or quoting, do not keep retrying variants. Simplify the command shape or switch to `apply_patch`, then continue.
- Treat the WSL NAT/localhost warning as noise only when the command exit code is 0.

## Version Management
- Keep `main` as the stable integration branch; do not commit accumulated task work directly to `main` unless the user explicitly requests it.
- Before committing, ensure the current branch starts with `codex/`. If currently on `main`, create or switch to a `codex/` branch first, for example `codex/task-8-inventory` or `codex/checkpoint-current-work`.
- Every completed TaskMaster task or subtask MUST have a git commit after its testStrategy passes, TaskMaster status is updated, and `docs/dev-log.md` is appended.
- Stage only files changed for the completed task/subtask, including `.taskmaster/tasks/tasks.json` and `docs/dev-log.md` when they changed.
- Before committing, run `git status --short` and `git diff --cached --name-only` to verify the staged scope.
- Create the commit with a concise task-specific message before sending the final response. Do not leave verified task/subtask work uncommitted unless the user explicitly says not to commit or a blocker prevents committing.
- If the working tree already contains multiple completed tasks, create a checkpoint commit on a `codex/` branch and list the included tasks in the final response.
