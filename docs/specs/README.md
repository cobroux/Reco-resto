# Spec-Driven Development (SDD)

Every feature goes through the same five steps, driven by `/sdd <need>` (see `CLAUDE.md` and `.claude/`).

| Step | Agent | Output in `docs/specs/<feature>/` | Human pause |
|------|-------|-----------------------------------|-------------|
| 1 | `sdd-spec` | `spec.md` (from `TEMPLATE.md`) | Yes — answer open questions, approve |
| 2 | `sdd-plan` | `plan.md` | Yes — approve architecture and contracts |
| 3 | `sdd-tasks` | `task.md` | Yes — approve tasks and traceability matrix |
| 4 | `sdd-impl` | code, tests, `impl.md` | No |
| 5 | `sdd-validate` | `validation.md` (OK / TO FIX) | Review the verdict |

Identifiers are stable across documents: B (behavior) → AC (acceptance criterion) → TASK → T (test).

## Team rules

- One feature = one branch (`feat/<feature>`) = one `docs/specs/<feature>/` folder, so two people never edit the same spec.
- The spec and plan are approved by a teammate other than the author before step 3 (recorded by setting `Status: approved` in `spec.md`).
- The PR includes the spec folder and links `validation.md`.
- Spec and plan are not edited during implementation; deviations go in `impl.md`.
