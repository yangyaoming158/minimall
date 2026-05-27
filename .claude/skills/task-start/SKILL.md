---
name: task-start
description: Open a MiniMall TaskMaster task with the project's standard ritual. Use when the user says "开始任务/下一个/继续 phaseN/start task <id>" or otherwise asks to begin a TaskMaster-tracked task. Confirms phase/branch alignment, pulls next/show from TaskMaster, recommends whether to split, presents an implementation plan, and STOPS for user confirmation. Read-only — does not edit files, does not change TaskMaster status, does not commit.
---

## Purpose
Frame every new TaskMaster task with the same opening: confirm phase + branch, read mandatory docs, pull the next task, propose a split, present a plan, stop for the user's "go".

## When to use
- User asks to start / continue / work on a TaskMaster task.
- New session, no explicit goal yet, but the next task is the obvious move.
- Do NOT use when the user is debugging a specific bug or asking a question that does not map to a task.

## Inputs
- Optional explicit task id (`task 2.2`, `继续 phase2 task 3`)
- Current TaskMaster tag (from `task-master tags` or `.taskmaster/state.json`)
- Current git branch (from `git branch --show-current`)

## Procedure
1. `git branch --show-current` and `git status --short`.
2. `node node_modules/task-master-ai/dist/task-master.js tags`. Note the `(current)` tag.
3. Phase drift check — compare three sources:
   - `CLAUDE.md` "Current phase" line (or other phase markers in CLAUDE.md)
   - TaskMaster `(current)` tag
   - git branch prefix (e.g. `codex/phase2-…`, or a recorded override like `feature/phase1-customer-frontend`)
   Specifically extract a phase number from each where possible:
   - Tag: leading `phaseN` in the tag name (e.g. `phase2-admin-platform` → `2`)
   - Branch: first `phase(\d+)` match in the branch name (e.g. `codex/phase2-admin-task1` → `2`)
   - CLAUDE.md: first `Phase\s*(\d+)` match
   If the extracted phase numbers disagree (or any source has no detectable phase), print a single warning line that names the specific mismatch — e.g. `drift: branch=phase2, tag=phase3 (consider git checkout -b codex/phase3-…)`. Soft warning only — do NOT auto-create branches, do NOT auto-fix CLAUDE.md, do NOT block. The user decides whether to switch branch before continuing.
4. `node node_modules/task-master-ai/dist/task-master.js next --tag=<current>`.
5. `node node_modules/task-master-ai/dist/task-master.js show <id>` (note: `show` does not accept `--tag`; rely on `(current)`).
6. Read in this order, only if not already in context:
   - `CLAUDE.md`
   - `README.md`
   - The current phase contract: `docs/<phase>-*-contract.md` if present
   - `.taskmaster/docs/<phase>-*-prd.txt`
7. Output a split recommendation in this exact form: `是否拆分：是/否，原因：…`. Prefer `add-subtask` over `expand` (expand has timed out/hung historically — see AGENTS.md).
8. Output the implementation plan:
   - Files expected to change
   - Verification commands (smallest applicable check)
9. STOP. Wait for explicit user confirmation before editing.

## Stop conditions
- After step 9 (plan presented). Do not edit, do not call `set-status --status=in-progress`, do not commit.
- If phase drift is detected, surface it and let the user decide whether to continue.
- If TaskMaster CLI fails or hangs, report the failure and stop (do NOT loop-retry; see AGENTS.md).

## Output format
```
Branch: <name>
TaskMaster tag: <tag>   [drift: <details> | aligned]
Task: <id> – <title>
是否拆分：是/否，原因：…
Plan:
  1. …
  2. …
Expected files:
  - <path>
Verification:
  $ <command>
Waiting for your "go" before any edit.
```

## Verification checklist
- [ ] Branch + tag + CLAUDE.md phase reconciled in output
- [ ] Next task id + title printed
- [ ] Split decision presented BEFORE the plan, not after
- [ ] Plan, expected files, verification commands all present
- [ ] No edits performed; no TaskMaster status change
- [ ] Output ends with an explicit "waiting" line
