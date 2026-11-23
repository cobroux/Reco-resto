# Contributing

## Workflow

1. Open or pick an issue (templates in `.github/ISSUE_TEMPLATE/`).
2. Create a branch from an up-to-date `main`: `feat/<slug>`, `fix/<slug>` or `chore/<slug>`.
3. For a feature, run `/sdd <need>` and get each SDD pause validated (see `docs/specs/README.md`).
4. Write tests first, then the minimum code (DRY: reuse or extend existing code).
5. Commit with Conventional Commits: `feat: ...`, `fix: ...`, `docs: ...`, `test: ...`, `chore: ...`, `refactor: ...`.
6. Push your branch and open a PR using the template; at least one teammate reviews and approves before merge.

## Rules

- Never commit directly to `main`; never force-push shared branches.
- English only: code, comments, docs, commits, PRs.
- All tests pass locally (`mvn -f backend test`, `npm --prefix frontend test -- --watch=false`) before opening a PR.
- No secrets in the repository; use `.env` (ignored) and update `.env.example`.
