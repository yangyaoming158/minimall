---
name: commit-gate
description: Manually commit an already-staged MiniMall task. Use ONLY when the user has just (this turn or the immediately previous turn) said "提交/go/commit/ship it" after seeing a task-finish wrap-up. Re-verifies the staged set, validates the branch, commits via heredoc, then verifies. Never pushes, never amends, never rebases.
disable-model-invocation: true
---

## Purpose
The committed gate guaranteed by `[[feedback-commit-authorization]]`: every commit needs a fresh per-commit "go" with the exact staged set visible at commit time.

## When to use
- User has just explicitly said "提交"/"go"/"commit"/"ship it" with the wrap-up report from `task-finish` visible.
- Never auto-invoke. This skill is `disable-model-invocation: true`.
- Do NOT use to amend, rebase, or push.

## Inputs
- Already-staged files (from `task-finish`)
- Proposed commit message (from `task-finish`)

## Procedure
1. Re-run `git status --short` and `git diff --cached --name-only`.
2. If the staged set drifted from the wrap-up report → STOP and ask the user to re-confirm (do not "fix" silently).
3. Validate branch:
   - Must start with `codex/` OR be an explicit user-stated override (e.g. `feature/phase1-customer-frontend` per the Phase 1 record — see memory `[[phase1-frontend-workflow]]`).
   - If on `main` / `master` → STOP. Ask the user to create or switch to a `codex/` branch first.
4. Commit using heredoc form only:
   ```
   git commit -m "$(cat <<'EOF'
   <subject line>

   <optional body, wrapping at ~72 cols>
   EOF
   )"
   ```
5. `git status` and `git log -1 --oneline` to confirm the new HEAD.
6. Report the new commit hash + subject. Do NOT chain `git push`, `git tag`, `git rebase`, or `git commit --amend`.

## Stop conditions
- Staged set differs from the wrap-up → STOP.
- Branch is `main`/`master` and no recorded override given → STOP.
- Pre-commit hook fails → STOP. Do NOT pass `--no-verify`. Do NOT `--amend`. Report the hook output; user decides next step (fix → re-stage → NEW commit).
- User did not say "go" in clear terms for THIS specific commit → STOP.
- The wrap-up report is missing from the session (no preceding `task-finish` output) → STOP and run `task-finish` first.

## Output format
```
Branch: <name>
Staged:
  - <file>
Commit: <short hash> <subject>
HEAD: <ref>
Done. (No push performed.)
```

## Verification checklist
- [ ] Staged set re-verified at commit time
- [ ] Branch starts with `codex/` or is a recorded explicit override
- [ ] heredoc commit form used
- [ ] No `--no-verify`, no `--amend`, no `git push`, no `git tag`
- [ ] Per-commit user authorization present in the same or previous turn
