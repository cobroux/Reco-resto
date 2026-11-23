# Reco-Resto

Team project (4 contributors). Backend and frontend live in one monorepo and follow Spec-Driven Development (SDD).

## Stack and layout

- `backend/`: Java 21, Spring Boot 4.1.1, Maven. Base package `hr.emocio.recoresto`.
- `frontend/`: Angular 22.
- `docs/specs/<feature>/`: SDD documents (spec, plan, task, impl, validation).
- `.claude/`: shared agents and commands (`/sdd`). Personal overrides go in `.claude/settings.local.json` (ignored).

## Commands

- Backend: `mvn -f backend test`, `mvn -f backend spring-boot:run`
- Frontend: `npm --prefix frontend install`, `npm --prefix frontend start`, `npm --prefix frontend test -- --watch=false`, `npm --prefix frontend run build`

## Conventions

- Everything is written in English: code, comments, docs, specs, commit messages, PRs.
- Branches: `feat/<slug>`, `fix/<slug>`, `chore/<slug>`. Never commit directly to `main`.
- Commits: Conventional Commits (`feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`).
- Tests first. Backend tests in `backend/src/test/java` (mirror the package of the class under test); frontend tests next to the code as `*.spec.ts`.
- Agents never push and never open PRs; humans do. Agents commit only when asked.

## SDD workflow

Run `/sdd <need>`. Steps and pauses (the human validates each pause):
1. `sdd-spec` → `spec.md` (WHAT/WHY, B/AC/T identifiers) — pause.
2. `sdd-plan` → `plan.md` (architecture, contracts) — pause.
3. `sdd-tasks` → `task.md` (tasks + traceability matrix) — pause.
4. `sdd-impl` → code, tests, `impl.md`.
5. `sdd-validate` → `validation.md` (verdict OK / TO FIX).

One feature per branch and per `docs/specs/<feature>/` folder. Details: `docs/specs/README.md`.

## Never guess

- If a request is ambiguous or information is missing, ask before acting. Ask all open questions in a single batch, before writing code.
- Always ask when missing or unclear: business values (rates, scales, thresholds, limits, dates) not readable from the code; intended behavior for edge cases and errors; the location, name or shape of what must be created when several options exist; whether an existing behavior may be changed.
- Do not fill a gap with an assumed value, even a plausible one. If you must proceed with an unverified value, say so in the final answer and flag it for validation.
- Do not ask about choices that follow an established convention of the code base or language; apply it and mention it.

## DRY

- Every piece of knowledge (logic, constant, rule, data table) has a single authoritative representation. Before writing code, search the code base (by behavior and related keywords) for something to reuse or extend; check logic, constants and data tables, not only functions.
- Extend or generalize existing code in place rather than adding a parallel version; update every caller and test if a signature changes; remove replaced code in the same change.
- Report what was reused, extended or replaced, or state that nothing relevant was found.
