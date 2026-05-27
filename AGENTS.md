# AGENTS.md

## Project
MiniMall Order is a Java 17 + Spring Boot 3 + Spring Cloud microservice order system.

## Task Workflow
Use TaskMaster as the source of truth.

Before implementing:
1. Confirm the active TaskMaster tag. For Phase 0 API Contract Polish work, use the `phase0-api-polish` tag explicitly and do not operate on `master`.
2. Run `task-master next`, or `task-master next --tag=phase0-api-polish` for Phase 0 work.
3. Run `task-master show <id>`, or `task-master show <id> --tag=phase0-api-polish` for Phase 0 work.
4. Explicitly state whether the task will be split before implementation, using `是否拆分：是/否，原因：...`. If the task is large or spans unclear boundaries, run `task-master expand --id=<id> --num=<n>`, adding `--tag=phase0-api-polish` for Phase 0 work.
5. Explain the implementation plan.
6. Wait for confirmation unless explicitly told to proceed.

For Phase 0 API Contract Polish:
- Treat `phase0-api-polish` as a separate task tree from the completed backend MVP.
- Use `--tag=phase0-api-polish` on TaskMaster read/write commands, or run `task-master use-tag phase0-api-polish` and verify with `task-master tags` before making status changes.
- Never run `parse-prd`, `set-status`, `expand`, or implementation workflow commands against `master` for Phase 0 work.
- Verify `master` remains the original 20/20 done backend MVP when changing the Phase 0 task tree.

During implementation:
- Implement only the current task or subtask.
- Do not modify unrelated modules.
- Do not introduce components outside the current task.
- Do not manually edit `.taskmaster/tasks/tasks.json` unless TaskMaster CLI/MCP is unavailable.
- Use TaskMaster commands whenever possible.
- In the current WSL Codex environment, if global `task-master` is unavailable, use the project-local CLI: `node node_modules/task-master-ai/dist/task-master.js COMMAND`, replacing `COMMAND` with `next --tag=phase0-api-polish`, `show 1 --tag=phase0-api-polish`, `set-status --id=1 --status=in-progress --tag=phase0-api-polish`, etc.
- TaskMaster CLI nuances (project-local CLI):
  - `--tag=<tag>` is accepted on `next`, `list`, `add-subtask`.
  - `--tag` is NOT accepted on `show` or `set-status`. For those, ensure the desired tag is current first (`use-tag <tag>`, verify with `tags`), then run the bare command.
  - `expand --id=<id> --num=<n>` has historically hung or failed in this WSL/Codex sandbox. If it errors or does not return promptly, fall back to manual `add-subtask --parent=<id>` for each subtask. Do not retry `expand` in a loop.
  - Do not hand-edit `.taskmaster/tasks/tasks.json` unless the CLI is fully unavailable.

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
- Current Codex sessions run inside WSL with project root `/home/oslab/projects/mini-mall-order`. Prefer direct commands from the project root, for example `node node_modules/task-master-ai/dist/task-master.js tags` and `mvn test`.
- Do not wrap commands in `wsl bash -lc` when already inside WSL. Use the PowerShell-to-WSL pattern only when explicitly operating from Windows PowerShell outside this environment.
- Prefer the WSL-local `node` command for TaskMaster. Do not use Windows Node paths such as `/mnt/d/nodejs/node.exe` unless WSL-local Node is missing.
- Prefer short, predictable commands and avoid clever shell composition.
- Do not use long inline `printf`/`echo` blocks to write files. Use `apply_patch` for source, docs, YAML, SQL, JSON, and dev-log edits.
- Avoid pipelines, command substitution, nested quotes, heredocs, semicolon chains, and regex alternation when a simpler command or separate tool call is enough.
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
