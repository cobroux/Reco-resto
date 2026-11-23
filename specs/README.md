# Spec-Driven Development with Spec Kit

Every feature goes through the same steps, driven by the `/speckit-*` skills (see `CLAUDE.md` and `.claude/skills/`). Spec Kit templates and the project constitution live in `.specify/`.

| Step | Skill | Output in `specs/<NNN>-<feature>/` | Human pause |
|------|-------|------------------------------------|-------------|
| 1 | `/speckit-specify <need>` | `spec.md`, `checklists/requirements.md` | Yes — answer open questions, approve |
| 2 | `/speckit-clarify` (optional) | `spec.md` updated | Yes |
| 3 | `/speckit-plan` | `plan.md` and design artifacts | Yes — approve architecture and contracts |
| 4 | `/speckit-tasks` | `tasks.md` | Yes — approve tasks |
| 5 | `/speckit-implement` | code and tests | No |
| 6 | `/speckit-analyze` (optional) | consistency report | Review the findings |

## Team rules

- One feature = one branch (`feat/<feature>`) = one `specs/<NNN>-<feature>/` folder, so two people never edit the same spec.
- The spec and plan are approved by a teammate other than the author before step 4 (recorded by setting `Status: Approved` in `spec.md`).
- The PR includes the spec folder.
- Spec and plan are not edited during implementation; deviations are reported in the PR.
