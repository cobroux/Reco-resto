---
description: Spec-Driven Development — chains spec → plan → tasks → implementation → validation with a validation pause between steps (need as argument)
argument-hint: <need or feature to build>
---

Orchestrate the SDD sub-agent team on this need:

$ARGUMENTS

If the argument is empty, ask the user to describe the need and stop.

Working folder: `docs/specs/<feature>/`, where `<feature>` is a short kebab-case name derived from the need. Announce it to the user; if it is ambiguous, ask. If the folder already exists, ask whether to resume at a given step or overwrite.

Sequence (each agent receives the need, the `<feature>` name and the files produced by the previous steps):
1. Agent `sdd-spec` → `spec.md`.
   **PAUSE**: present the spec and its open questions, ask them in a single batch, have `spec.md` completed with the answers (re-run `sdd-spec`), then wait for the user's explicit approval and set the status to `approved`.
2. Agent `sdd-plan` → `plan.md`.
   **PAUSE**: present architecture, stack, contracts and open questions; wait for explicit approval.
3. Agent `sdd-tasks` → `task.md`.
   **PAUSE**: present the tasks and the traceability matrix; wait for explicit approval.
4. Agent `sdd-impl` → code, tests, documentation and `impl.md`.
5. Agent `sdd-validate` → `validation.md`. If the verdict is `TO FIX`, send the points back to `sdd-impl` then re-run `sdd-validate` (2 loops maximum, then escalate the blocker to the user).

Reminders: never guess, never fill an open question yourself, escalate it to the user. No `git push` and no `gh` (humans push and open PRs); no `git commit` unless the user asks. Conclude with a recap: files produced in `docs/specs/<feature>/`, test results, validation verdict and any unverified point to validate.
