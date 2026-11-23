# Reco-Resto

<!-- TODO(team): one-paragraph product description. Not defined yet. -->

Monorepo: `backend/` (Spring Boot) and `frontend/` (Angular). Work follows Spec-Driven Development; see `docs/specs/README.md` and `CONTRIBUTING.md`.

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js (current LTS or newer) and npm

## Install and run

```bash
# backend
mvn -f backend spring-boot:run

# frontend
npm --prefix frontend install
npm --prefix frontend start
```

Copy `.env.example` to `.env` for local configuration (never commit `.env`).

## Tests

```bash
mvn -f backend test
npm --prefix frontend test -- --watch=false
```
