# Implementation Plan: Natural-Language Restaurant Search

**Branch**: `feat/natural-language-search` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-natural-language-search/spec.md`

## Summary

The backend receives a French sentence (and optionally a device position), asks an LLM to turn it into
structured search criteria (cuisine, diet, place, moment, distance, AND/OR grouping), resolves the place
to coordinates with the API Adresse (data.gouv.fr), then asks the LLM for the matching restaurants
(decision of 2026-11-24: there is no restaurant API, the LLM provides the restaurants, with web access
given by the backend through tool calling on a web search API: see research.md R2). The backend
validates and filters the LLM answer (radius, opening hours at the targeted moment, address present),
ranks it and returns the deck (15 by default) plus the full zone list on demand. The backend is
stateless: the understood criteria are returned to the client, which keeps them for features 003 and 005.

The LLM is the one provided by the team: model `nova-lite` behind an OpenAI-compatible
`/v1/chat/completions` endpoint (see [research.md](research.md), R1). The performance thresholds,
the relevance rule and the moment times are **deferred** by the team; they stay marked
`NEEDS CLARIFICATION`. See the risk in R2: restaurants produced by an LLM may be inaccurate.

## Technical Context

**Language/Version**: Java 21 (backend, `backend/`, does not exist yet; setup chore required, see Assumptions of the spec)

**Primary Dependencies**: Spring Boot 4.1.1, Maven. LLM client: plain HTTP call to an OpenAI-compatible chat-completions endpoint. Restaurants: provided by the same LLM (no external restaurant API), which calls a web search tool executed by the backend (tool calling). Web access: headless Chrome driven by the backend (decision of 2026-11-24); the automation library and the search engine opened in it: NEEDS CLARIFICATION. Geocoding: API Adresse (data.gouv.fr), HTTP.

**Storage**: none for this feature (no persistence of searches). Session criteria (FR-014): none on the server; the backend is stateless and returns the criteria to the client.

**Testing**: JUnit via `mvn -f backend test`; tests in `backend/src/test/java`, mirroring `hr.emocio.recoresto`. LLM and data source are faked in unit tests; one opt-in test class runs the reference sentences against the real LLM (SC-003).

**Target Platform**: Linux server (JVM) consumed by the Angular 22 frontend (`frontend/`).

**Project Type**: web-service (backend REST API) consumed by the existing web frontend.

**Performance Goals**: SC-001 threshold NEEDS CLARIFICATION (set by the team).

**Constraints**:
- Secrets (LLM key) only in `.env` (ignored); `.env.example` updated with variable names only (constitution, Technology constraints).
- Deck size (15) and default radius (2.5 km) are configuration values, not code constants (FR-004, FR-008).
- France only (FR-010).
- Privacy: the sentence and position are sent to the LLM endpoint and possibly to geocoding and data-source providers; NEEDS CLARIFICATION (validation by the team required by the spec).

**Scale/Scope**: MVP, 4 contributors; no volume target given. NEEDS CLARIFICATION if one is expected.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|-----------|--------|------|
| I. Spec-Driven Development | Pass | One feature, one branch, one `specs/002-...` folder. Plan MUST be approved by a teammate other than the author before `/speckit-tasks`. |
| II. Test-First | Pass (to enforce) | Tasks MUST list tests before code; see Testing above. |
| III. Never Guess | **Open** | Thresholds (SC-001, SC-003), moment times, relevance rule, privacy and LLM data reliability remain unresolved and are left as NEEDS CLARIFICATION. Not a violation as long as they are not filled with assumed values. |
| IV. DRY | Pass | No backend code exists yet; nothing to reuse. Single authoritative place for criteria schema (see data-model.md) and for configuration values. |
| V. English and Conventional Commits | Pass | |

Post-design re-check: unchanged. The gate is passed only for the parts that do not depend on the open
items; `/speckit-tasks` MUST NOT start until they are closed.

## Project Structure

### Documentation (this feature)

```text
specs/002-natural-language-search/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── search-api.openapi.yaml
└── tasks.md             # /speckit-tasks, not created here
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                         # setup chore (not in this feature)
├── src/main/java/hr/emocio/recoresto/search/
│   ├── api/            # REST controller, request/response DTOs
│   ├── criteria/       # SearchCriteria model, LLM-based CriteriaExtractor
│   ├── llm/            # OpenAI-compatible chat client (config: base URL, key, model)
│   ├── place/          # place resolution (API Adresse client), France check
│   ├── restaurant/     # RestaurantSource port + LLM adapter, answer validation, opening-hours/radius/address filters, ranking
│   └── config/         # SearchProperties: radius, deck size
└── src/test/java/hr/emocio/recoresto/search/       # mirrors the packages above
frontend/                                           # consumption of the API belongs to features 001/003/005
```

**Structure Decision**: backend-only change in `backend/`, package `hr.emocio.recoresto.search`.
The restaurant source stays behind a port (interface) so a real data source can replace the LLM later
without touching the rest. Package names are proposed and subject to validation at the plan review.

## Complexity Tracking

No constitution violation to justify.

## Open Items (to close before `/speckit-tasks`)

| # | Item | Needed for | Owner |
|---|------|------------|-------|
| O1 | CLOSED 2026-11-24: no restaurant API; the LLM provides restaurants. New risk R2-a: reliability of LLM-provided data (see research.md R2) | FR-007, FR-012, FR-013 | Team |
| O2 | CLOSED 2026-11-24: API Adresse (data.gouv.fr). To verify: out-of-France detection by absence of result | FR-006, FR-010, FR-015 | Developer |
| O3 | SC-001 threshold (seconds, percentile): deferred by the team | SC-001 | Team |
| O4 | SC-003 target rate, reference set size and author: deferred by the team | SC-003 | Team |
| O5 | Privacy validation: sentence/position to the LLM endpoint, API Adresse and the web search provider | Assumptions | Team |
| O10 | PARTLY CLOSED 2026-11-24: web access = headless Chrome driven by the backend. Still open: automation library (e.g. Playwright for Java or Selenium), search engine used, whether its terms of use allow automated access (scraping Google results is forbidden by its terms), Chrome installation and resources on the deployment target | FR-007, FR-012 | Team |
| O6 | CLOSED 2026-11-24: stateless backend, criteria returned to the client | FR-014 | - |
| O9 | Moment times ("ce soir", "midi") and relevance rule: deferred by the team | FR-007, FR-008 | Team |
| O7 | Claude Design project not available to the author; FR-012 data shape not cross-checked against screens | FR-012 | Author |
| O8 | Whether the LLM endpoint supports tool calling (now REQUIRED by the web-access choice) and JSON mode; not verified, no key available to the author | R1, R2 | Developer |
