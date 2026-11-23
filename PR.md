## Summary
Team project setup for Reco-Resto (4 contributors, Spec-Driven Development with Spec Kit):

- Spec Kit workflow: `.specify/` (templates, scripts, constitution) and `/speckit-*` skills in `.claude/skills/`, replacing the former custom `/sdd` command and agents.
- `.claude/settings.json`: agents may not `git push` or `gh pr create` (humans push and open PRs).
- `CLAUDE.md` (stack, commands, conventions, Spec Kit workflow, "Never guess" and "DRY" rules), `README.md`, `CONTRIBUTING.md`.
- `specs/README.md` (Spec Kit process and team rules) and the first spec `specs/001-restaurant-search-bar/`.
- `.github/` PR template and issue templates (feature, bug), `.editorconfig`.
- Angular 22.3.0 frontend generated in `frontend/`.

Closes #<issue> <!-- no issue number defined: fill in or remove -->

## Spec Kit
- Spec folder: n/a (chore, project setup)
- Validation verdict: n/a

## Not included (follow-ups)
- Spring Boot backend (`backend/`): Maven is not installed on the author's machine, so it could not be built or tested.
- The custom SDD agents and `/sdd` were replaced by Spec Kit (`.specify/`, `.claude/skills/speckit-*`, `specs/`); `docs/specs/` moved to `specs/`.
- `.env.example`, `CODEOWNERS` (needs the 4 GitHub handles), CI workflow, branch protection on `main`.

## To validate
- Spring Boot 4.1.1 (written in `CLAUDE.md`) comes from a secondary source, not spring.io.
- `README.md` product description is a TODO.
- Git warns about LF → CRLF conversion on Windows (`.editorconfig` asks for LF); consider adding a `.gitattributes`.

## Checklist
- [ ] Tests written first and passing: frontend only (`npm --prefix frontend test -- --watch=false`, 2 tests pass); backend does not exist yet
- [x] Existing code reused or extended: obsolete SDD agents, command and `docs/specs/` removed in the same change, nothing duplicated in the repo
- [x] Documentation updated
- [x] Commits follow Conventional Commits
- [x] No secrets or local files committed

🤖 Generated with [Claude Code](https://claude.com/claude-code)
