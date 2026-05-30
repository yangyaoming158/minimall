---
name: task-finish
description: Wrap up a finished MiniMall TaskMaster task right before the commit step. Use when the user says "做完了/收尾/wrap up/写 dev-log/任务完成". Runs the smallest matching verification, invokes gateway-contract-audit when frontend files changed, appends a structured dev-log entry (9 fields), stages only this task's files, prints a commit-message proposal, and STOPS. Does NOT run git commit — that belongs to commit-gate with a fresh per-commit "go".
---

## Purpose
Standardize task wrap-up: verify → write dev-log → propose a commit. Never crosses the commit line.

## When to use
- TaskMaster work is done; commit has not happened yet.
- Do NOT use mid-task to "save progress" — use `handoff` for that.
- Do NOT use for documentation-only edits that already have their own dev-log handling.

## Inputs
- Current task id (from TaskMaster state or the last task-start output)
- Changed files (from `git status --short`)
- TaskMaster `(current)` tag

## Procedure
1. `git status --short` and `git diff --stat`.
2. Pick the smallest verification that matches the changed scope:
   - Frontend files only → `cd frontend && npm run build` (or `npm run type-check` for type-only changes)
   - Single backend module → `mvn -pl <module> -am test`
   - Cross-module backend → `mvn clean package -DskipTests`
   - Docs / config only → skip build; note `documentation-only` in dev-log "Test result"
2b. MANDATORY full-build gate (hard rule): if ANY backend (`*.java`, `pom.xml`, `src/main/resources/**`, or SQL migration) file changed, you MUST also run `mvn clean package -DskipTests` for the full reactor before staging — regardless of how narrow the scope looks. The scoped check in step 2 does not satisfy this. A single-module test pass is NOT sufficient to commit; the full reactor must compile and package. If this gate fails, treat it as verification FAIL (see Stop conditions). Skip ONLY for docs/config-only or frontend-only changes with no backend file touched.
3. If `frontend/src/**` or `admin-frontend/src/**` changed → invoke skill `gateway-contract-audit`. Record its pass/fail summary in the dev-log entry.
4. Append a new entry to `docs/dev-log.md` using exactly this template (preserve field order, keep "Status: Done"):
   ```
   ## Task <id> - <title>
   - Date: <YYYY-MM-DD>
   - Status: Done
   - TaskMaster tag: <tag>
   - Implemented: <one paragraph describing what was done>
   - Changed files: <semicolon-separated paths>
   - Commands run: <semicolon-separated commands>
   - Test result: <verification outcome + audit outcome if applicable>
   - Issues: <or "None">
   - Next: <next task id - title, from TaskMaster next>
   ```
5. Stage only this task's files: `git add <explicit paths>`, including `.taskmaster/tasks/tasks.json` and `docs/dev-log.md` if they changed. NEVER `git add -A` or `git add .`.
6. Print wrap-up report (output format below).
7. STOP. Do not run `git commit`. The commit requires a fresh user "go" via `commit-gate`.

## Stop conditions
- After step 7. Next step belongs to `commit-gate` with explicit per-commit authorization (see [[feedback-commit-authorization]]).
- If verification FAILS → do NOT append a "Status: Done" dev-log entry; report the failure, leave the working tree as-is, stop.
- If gateway-contract-audit fails → mark wrap-up FAIL and stop (do not stage, do not write dev-log Done entry).

## Output format
```
Verification: pass | fail – <one-line detail>
Frontend audit: skipped | pass | fail   [findings: file:line]
dev-log entry appended: <heading line>
Staged files:
  - <path>
Proposed commit message:
  <subject line>
Remaining risks: <or "None">
Next task: <id> – run /task-start when ready
Waiting for your "go" before commit.
```

## Verification checklist
- [ ] Verification command matched the changed scope
- [ ] If any backend file changed, full `mvn clean package -DskipTests` ran and passed (mandatory gate, step 2b)
- [ ] dev-log entry has all 9 fields filled (no blanks)
- [ ] `git add -A` / `git add .` not used
- [ ] No `git commit` executed
- [ ] If frontend touched, audit result is recorded in dev-log
- [ ] Output ends with an explicit "waiting" line
