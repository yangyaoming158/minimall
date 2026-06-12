---
name: handoff
description: Record a mid-task handoff so a fresh session can resume. Use ONLY when the user says "做个交接/handoff/上下文要满了/暂停一下" or otherwise asks to pause an in-progress task. Appends a structured in-progress entry to docs/dev-log.md. Does NOT commit, does NOT run verification, does NOT mark the task done. Manual only.
disable-model-invocation: true
---

## Purpose
Capture in-progress state so the next session can continue without re-deriving context. Distinct from `task-finish`: `task-finish` declares "Status: Done"; `handoff` declares "in-progress".

## When to use
- User explicitly asks for a handoff or warns about context limits.
- Task is NOT done.
- Never auto-invoke. This skill is `disable-model-invocation: true`.
- Do NOT use as a substitute for `task-finish`.

## Inputs
- Current task id and title
- Current branch
- What's been done so far (bullet-able)
- The very next step (exact command or edit, not a vague verb)

## Procedure
1. `git branch --show-current`, `git status --short`, `git diff --stat`.
2. Append to `docs/dev-log.md` using exactly this template:
   ```
   ## Handoff <YYYY-MM-DD HH:MM> - Task <id> - <title> (in-progress)
   - Branch: <name>
   - TaskMaster tag: <tag>
   - Progress so far:
     - <bullet>
     - <bullet>
   - Files touched (uncommitted): <semicolon-separated paths, or "None">
   - Verified: <what was verified so far, or "nothing yet">
   - Next step: <exact command or edit to run next session>
   - Blockers: <or "None">
   ```
3. STOP. Do not stage, do not commit, do not run `set-status --status=done`.

## Stop conditions
- After the dev-log entry is appended.
- If TaskMaster status was mistakenly set to `done` earlier in the session, flag it in the handoff entry under "Blockers" but do NOT auto-revert.
- If verification has not been run, write `Verified: nothing yet` — do not silently invent a result.

## Output format
- One line confirming the handoff entry was appended (quote the heading line).
- A one-line summary: "Next session should run: `<exact command>`".

## Verification checklist
- [ ] Heading uses "Handoff" + date + task id (NOT "Task <id> - Done")
- [ ] "Next step" is a concrete command or edit, not a vague verb
- [ ] No git commit was executed
- [ ] TaskMaster status untouched
- [ ] Output names the next session's first command
