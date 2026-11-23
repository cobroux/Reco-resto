# Reco-Resto

<!-- TODO(team): one-paragraph product description. Not defined yet. -->

Monorepo: `backend/` (Spring Boot) and `frontend/` (Angular). Work follows Spec-Driven Development with Spec Kit; see `specs/README.md` and `CONTRIBUTING.md`.

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js (current LTS or newer) and npm
- [uv](https://docs.astral.sh/uv/) and the Spec Kit CLI, only to write specs: `uv tool install specify-cli --from git+https://github.com/github/spec-kit.git`

## Install and run

```bash
# backend
mvn -f backend spring-boot:run

# frontend
npm --prefix frontend install
npm --prefix frontend start
```

Copy `.env.example` to `.env` for local configuration (never commit `.env`).

## Specs

Features are specified with Spec Kit in Claude Code: start with `/speckit-specify <need>`. Specs live in `specs/<NNN>-<feature>/`; the workflow is described in `specs/README.md`.

## Tests

```bash
mvn -f backend test
npm --prefix frontend test -- --watch=false
```
