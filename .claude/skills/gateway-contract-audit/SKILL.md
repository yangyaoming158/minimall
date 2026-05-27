---
name: gateway-contract-audit
description: Audit MiniMall frontend code for gateway-contract violations. Use after any change under frontend/src or admin-frontend/src — auto-invoked by task-finish, can also be invoked manually for a spot check. Runs three grep checks (X-User-Id/X-Username, /internal/, service ports 8101-8106) and reports pass/fail with file:line for any hit. Read-only — never edits files.
---

## Purpose
Catch the three frontend-side violations the gateway already strips/blocks. The customer/admin frontend must never source these from its own code.

## When to use
- After any change under `frontend/src/**` or `admin-frontend/src/**`.
- Invoked automatically by `task-finish` when frontend files changed.
- Can be invoked manually for a spot check (e.g. before a Phase acceptance).

## Inputs
- Optional target directory (default: every existing one of `frontend/src`, `admin-frontend/src`).

## Procedure
1. Determine target dirs: include `frontend/src` and `admin-frontend/src` only if they exist.
2. For each target, run the three checks. Exclude test fixtures.
   ```
   grep -rn --exclude-dir=__tests__ -E "X-User-Id|X-Username" <target>
   grep -rn --exclude-dir=__tests__ "/internal/" <target>
   grep -rn --exclude-dir=__tests__ -E ":(810[1-6])" <target>
   ```
3. Each check is `pass` (0 hits) or `fail` (list every `file:line: matched line`).
4. Print the verdict table per target.

## Stop conditions
- Any check fails → audit verdict = FAIL. Surface findings. Do NOT modify files. The caller (`task-finish` or the user) decides whether the failure blocks the wrap-up.
- A target directory is missing → skip it, report `skipped: not present`.
- `grep` exits non-zero only due to "no matches" (exit 1) → treat as PASS.

## Output format
```
Audit target: <dir>
  X-User-Id/X-Username : pass | fail (<n> hits)
  /internal/           : pass | fail (<n> hits)
  service ports        : pass | fail (<n> hits)
[fail details]
  <file>:<line>: <matched line>
```

If multiple targets exist, repeat the block per target and finish with an overall verdict line:
```
Overall: pass | fail
```

## Verification checklist
- [ ] Three greps ran with `--exclude-dir=__tests__`
- [ ] Each line is `pass` or shows file:line for every hit
- [ ] No file content was modified
- [ ] Skipped targets explicitly reported
- [ ] Overall verdict matches per-target results
