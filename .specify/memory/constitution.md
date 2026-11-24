# Reco-Resto Constitution

## Core Principles

### I. Spec-Driven Development
Every feature MUST go through Spec Kit: `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`,
then `/speckit-implement`. One feature MUST live in one branch and one `specs/<NNN>-<feature>/`
folder. The spec and plan MUST be approved by a teammate other than the author before tasks are
generated, and MUST NOT be edited during implementation; deviations are reported in the PR.
Rationale: four contributors work in one monorepo and must not edit the same spec.

### II. Test-First
Tests MUST be written before the code they cover and MUST fail before the implementation makes
them pass. Backend tests live in `backend/src/test/java`, mirroring the package of the class under
test. Frontend tests live next to the code as `*.spec.ts`. All tests MUST pass locally before a PR
is opened.

### III. Never Guess
When a request is ambiguous or information is missing, contributors and agents MUST ask before
acting, all open questions in a single batch before any code is written. Business values (rates,
scales, thresholds, limits, dates), edge-case and error behavior, the location, name or shape of
what is created, and any change to existing behavior MUST NOT be assumed. A value that could not be
verified MUST be flagged for validation. Choices that follow an established convention of the code
base or language are applied without asking and mentioned.

### IV. DRY
Every piece of knowledge (logic, constant, rule, data table) MUST have a single authoritative
representation. Before writing code, the code base MUST be searched, by behavior and related
keywords, for something to reuse or extend. Existing code MUST be extended in place rather than
duplicated; every caller and test MUST be updated when a signature changes; replaced code MUST be
removed in the same change. The result MUST report what was reused, extended or replaced.

### V. English and Conventional Commits
Code, comments, docs, specs, commit messages and PRs MUST be written in English. Commits MUST follow
Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:`). Branches MUST be
named `feat/<slug>`, `fix/<slug>` or `chore/<slug>`.

## Technology and Repository Constraints

- Backend: Java 21, Spring Boot 4.1.1, Maven, base package `hr.emocio.recoresto`, in `backend/`.
- Frontend: Angular 22, in `frontend/`.
- Backend and frontend live in one monorepo.
- Secrets MUST NOT be committed; local configuration goes in `.env` (ignored) and `.env.example`
  MUST be kept up to date.

## Development Workflow and Quality Gates

- Nobody commits directly to `main`, and shared branches are never force-pushed.
- Agents MUST NOT push or open PRs; humans do. Agents commit only when asked.
- Every PR uses the repository template, includes the spec folder for a feature, and is approved by
  at least one teammate before merge.
- Quality gate before a PR: `mvn -f backend test` and `npm --prefix frontend test -- --watch=false`
  pass.

## Governance

This constitution supersedes other practices in the repository. `CLAUDE.md` is the runtime guidance
file for agents and MUST stay consistent with it.

- **Amendments**: proposed by PR that edits this file, approved by at least one teammate other than
  the author, with the version and the amendment date updated in the same PR.
- **Versioning**: semantic. MAJOR for a removed or redefined principle, MINOR for a new principle or
  section or materially expanded guidance, PATCH for wording and clarifications.
- **Compliance**: every PR review MUST check compliance with the principles above; any deviation
  MUST be justified in the PR description.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): not ratified by the team yet | **Last Amended**: 2026-11-24
