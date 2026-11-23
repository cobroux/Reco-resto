## Summary
Team project setup for Reco-Resto (4 contributors, Spec-Driven Development):

- Shared Claude workflow in `.claude/`: `/sdd` command and agents `sdd-spec`, `sdd-plan`, `sdd-tasks`, `sdd-impl`, `sdd-validate`, plus `launch-project`, `launch-test`, `code-analyse` (English, adapted to Java/Angular).
- `.claude/settings.json`: agents may not `git push` or `gh pr create` (humans push and open PRs).
- `CLAUDE.md` (stack, commands, conventions, SDD workflow, "Never guess" and "DRY" rules), `README.md`, `CONTRIBUTING.md`.
- `docs/specs/TEMPLATE.md` and `docs/specs/README.md` (SDD process and team rules).
- `.github/` PR template and issue templates (feature, bug), `.editorconfig`.
- Angular 22.3.0 frontend generated in `frontend/`.

Closes #<issue> <!-- no issue number defined: fill in or remove -->

## SDD
- Spec folder: n/a (chore, project setup)
- Validation verdict: n/a

## Not included (follow-ups)
- Spring Boot backend (`backend/`): Maven is not installed on the author's machine, so it could not be built or tested.
- Replacing the custom SDD agents with Spec Kit (not installed yet).
- `.env.example`, `CODEOWNERS` (needs the 4 GitHub handles), CI workflow, branch protection on `main`.

## To validate
- Spring Boot 4.1.1 (written in `CLAUDE.md`) comes from a secondary source, not spring.io.
- `README.md` product description is a TODO.
- Git warns about LF → CRLF conversion on Windows (`.editorconfig` asks for LF); consider adding a `.gitattributes`.

## Checklist
- [ ] Tests written first and passing: frontend only (`npm --prefix frontend test -- --watch=false`, 2 tests pass); backend does not exist yet
- [x] Existing code reused or extended: SDD agents copied from the author's personal `~/.claude` and adapted, nothing duplicated in the repo
- [x] Documentation updated
- [x] Commits follow Conventional Commits
- [x] No secrets or local files committed

🤖 Generated with [Claude Code](https://claude.com/claude-code)
