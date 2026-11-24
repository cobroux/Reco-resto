# Reco-Resto

Team project (4 contributors). Backend and frontend live in one monorepo and follow Spec-Driven Development with Spec Kit.

## Stack and layout

- `backend/`: Java 21, Spring Boot 4.1.1, Maven. Base package `hr.emocio.recoresto`.
- `frontend/`: Angular 22.
- `specs/<NNN>-<feature>/`: Spec Kit documents (spec, plan, tasks, checklists). `.specify/`: Spec Kit templates, scripts and constitution.
- `.claude/`: shared settings and Spec Kit skills (`/speckit-*`). Personal overrides go in `.claude/settings.local.json` (ignored).

## Commands

- Backend: `mvn -f backend test`, `mvn -f backend spring-boot:run`
- Frontend: `npm --prefix frontend install`, `npm --prefix frontend start`, `npm --prefix frontend test -- --watch=false`, `npm --prefix frontend run build`

## Conventions

- Everything is written in English: code, comments, docs, specs, commit messages, PRs.
- Branches: `feat/<slug>`, `fix/<slug>`, `chore/<slug>`. Never commit directly to `main`.
- Commits: Conventional Commits (`feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`).
- Tests first. Backend tests in `backend/src/test/java` (mirror the package of the class under test); frontend tests next to the code as `*.spec.ts`.
- Agents never push and never open PRs; humans do. Agents commit only when asked.

## Spec Kit workflow

Run the skills in order (the human validates each pause):
1. `/speckit-specify <need>` → `specs/<NNN>-<feature>/spec.md` (WHAT/WHY) — pause.
2. `/speckit-clarify` (optional) → resolve open questions in the spec.
3. `/speckit-plan` → `plan.md` (architecture, contracts) — pause.
4. `/speckit-tasks` → `tasks.md` — pause.
5. `/speckit-implement` → code and tests (tests first).
6. `/speckit-analyze` (optional) → cross-document consistency report.

One feature per branch and per `specs/<NNN>-<feature>/` folder. Details: `specs/README.md`.

## Never guess

- If a request is ambiguous or information is missing, ask before acting. Ask all open questions in a single batch, before writing code.
- Always ask when missing or unclear: business values (rates, scales, thresholds, limits, dates) not readable from the code; intended behavior for edge cases and errors; the location, name or shape of what must be created when several options exist; whether an existing behavior may be changed.
- Do not fill a gap with an assumed value, even a plausible one. If you must proceed with an unverified value, say so in the final answer and flag it for validation.
- Do not ask about choices that follow an established convention of the code base or language; apply it and mention it.

## DRY

- Every piece of knowledge (logic, constant, rule, data table) has a single authoritative representation. Before writing code, search the code base (by behavior and related keywords) for something to reuse or extend; check logic, constants and data tables, not only functions.
- Extend or generalize existing code in place rather than adding a parallel version; update every caller and test if a signature changes; remove replaced code in the same change.
- Report what was reused, extended or replaced, or state that nothing relevant was found.
