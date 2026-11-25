# Implementation Plan: Natural-Language Restaurant Search

**Branch**: `feat/natural-language-search` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-natural-language-search/spec.md`

## Summary

The backend receives a French sentence (and optionally a device position) and hands it to an LLM
(OpenAI-compatible endpoint, model `nova-lite`). Decision of 2026-11-25 (pivot, supersedes the
2026-11-24 decision of an LLM with a headless-Chrome web tool): restaurants come from **Overpass
(OpenStreetMap)**. The LLM understands the sentence (criteria, place, moment, distance, AND/OR grouping)
and writes Overpass queries that the backend runs through `OverpassClient`, within bounds (query length,
answer size, number of calls per search, timeout). The backend validates and filters the result (radius,
opening hours at the targeted moment, address present), ranks it and returns the deck (15 by default) plus
the full zone list on demand. The backend is stateless: the understood criteria are returned to the
client, which keeps them for features 003 and 005.

Evidence of the pivot in the working tree (not in any earlier plan version): `OverpassClient`,
`OverpassProperties`, `SearchAnswer` (understood / outside France / criteria / centers / restaurants),
`infra/overpass/docker-compose.yml` (local instance loaded with the Geofabrik France extract),
`backend/dev/overpass-fixture.json` (development fixture, data NOT real), and `HttpSearchService` in the
frontend replacing the stub. `CriteriaExtractor`, `ApiAdresseClient`, `PlaceResolver`,
`ResolvedPlace` and `GeocoderProperties` are deleted in the index. The LLM resolves places and detects out-of-France (decision of 2026-11-25); the
validation rule for coordinates and France is open (O12).

The LLM is the one provided by the team: model `nova-lite` behind an OpenAI-compatible
`/v1/chat/completions` endpoint (see [research.md](research.md), R1). The performance thresholds,
the relevance rule and the moment times are **deferred** by the team; they stay marked
`NEEDS CLARIFICATION`. See R2: the data now comes from OpenStreetMap, whose completeness (opening hours, cuisine, diet tags, phone, photo, menu) is NOT verified; fields absent in OSM stay absent (never invented).

## Technical Context

**Language/Version**: Java 21 (backend, `backend/`, exists)

**Primary Dependencies**: Spring Boot 4.1.1, Maven. LLM client: plain HTTP call to an OpenAI-compatible chat-completions endpoint. Restaurants: Overpass API (OpenStreetMap) queried over HTTP by `OverpassClient`, with queries written by the LLM (the LLM runs them by tool calling, decision of 2026-11-25; endpoint support not verified, O8). Overpass instance: public instances refuse anonymous default clients (HTTP 406 observed 2026-11-25), so a generic `User-Agent` is sent; a local instance is provided in `infra/overpass` (about 5 GB download, size and build time not measured). Place resolution and out-of-France detection are done by the LLM (decision of 2026-11-25; validation rule open, O12). The headless Chrome web tool is dropped.

**Storage**: none for this feature (no persistence of searches). Session criteria (FR-014): none on the server; the backend is stateless and returns the criteria to the client.

**Testing**: JUnit via `mvn -f backend test`; tests in `backend/src/test/java`, mirroring `hr.emocio.recoresto`. LLM and data source are faked in unit tests; one opt-in test class runs the reference sentences against the real LLM (SC-003).

**Target Platform**: Linux server (JVM) consumed by the Angular 22 frontend (`frontend/`).

**Project Type**: web-service (backend REST API) consumed by the existing web frontend.

**Performance Goals**: SC-001 threshold NEEDS CLARIFICATION (set by the team).

**Constraints**:
- Secrets (LLM key) only in `.env` (ignored); `.env.example` updated with variable names only (constitution, Technology constraints).
- Deck size (15) and default radius (2.5 km) are configuration values, not code constants (FR-004, FR-008).
- France only (FR-010).
- Privacy: the sentence and position are sent to the LLM endpoint; Overpass queries (which contain coordinates) go to the Overpass instance (public or local); NEEDS CLARIFICATION (validation by the team required by the spec).
- Overpass bounds (`overpass.*` in configuration: timeout, max response bytes, max query chars, max calls per search): values not validated by the team yet.

**Scale/Scope**: MVP, 4 contributors; no volume target given. NEEDS CLARIFICATION if one is expected.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|-----------|--------|------|
| I. Spec-Driven Development | Pass | One feature, one branch, one `specs/002-...` folder. Plan MUST be approved by a teammate other than the author before `/speckit-tasks`. |
| II. Test-First | Pass (to enforce) | Tasks MUST list tests before code; see Testing above. |
| III. Never Guess | **Open** | Thresholds (SC-001, SC-003), moment times, relevance rule, privacy and LLM data reliability remain unresolved and are left as NEEDS CLARIFICATION. Not a violation as long as they are not filled with assumed values. |
| IV. DRY | Pass (to enforce) | Backend code now exists: `LlmRestaurantSource` (LLM-provided restaurants) overlaps with the Overpass path and MUST be extended or replaced in place, not duplicated. Single authoritative place for criteria schema (see data-model.md) and for configuration values. |
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
├── pom.xml
├── dev/overpass-fixture.json                       # development fixture, data NOT real

├── src/main/java/hr/emocio/recoresto/search/
│   ├── api/            # REST controller, request/response DTOs
│   ├── criteria/       # SearchCriteria model, MomentResolver (criteria extracted by the LLM in the same exchange)
│   ├── llm/            # OpenAI-compatible chat client (config: base URL, key, model)
│   ├── place/          # removed: the LLM resolves places (research.md R3)
│   ├── restaurant/     # RestaurantSource port, OverpassClient, SearchAnswer, answer validation, opening-hours/radius/address filters, ranking
│   └── config/         # SearchProperties (radius, deck size), LlmProperties, OverpassProperties
└── src/test/java/hr/emocio/recoresto/search/       # mirrors the packages above
frontend/                                           # HttpSearchService replaces the stub (src/app/search)
infra/overpass/                                     # docker-compose of a local Overpass instance (France extract)
```

**Structure Decision**: backend change in `backend/` (package `hr.emocio.recoresto.search`), plus the frontend HTTP service and `infra/overpass`.
The restaurant source stays behind a port (interface) so a real data source can replace the LLM later
without touching the rest (here: Overpass replaces the LLM-as-data-source). Package names are proposed and subject to validation at the plan review.

## Complexity Tracking

No constitution violation to justify.

## Open Items (to close before `/speckit-tasks`)

| # | Item | Needed for | Owner |
|---|------|------------|-------|
| O1 | SUPERSEDED 2026-11-25: restaurants come from Overpass (OpenStreetMap), not from the LLM; see O11 and O13 | FR-007, FR-012, FR-013 | Team |
| O2 | SUPERSEDED 2026-11-25: the LLM resolves places; see O12 | FR-006, FR-010, FR-015 | Developer |
| O3 | SC-001 threshold (seconds, percentile): deferred by the team | SC-001 | Team |
| O4 | SC-003 target rate, reference set size and author: deferred by the team | SC-003 | Team |
| O5 | Privacy validation: sentence/position to the LLM endpoint and queries with coordinates to Overpass (public or local) | Assumptions | Team |
| O10 | CLOSED 2026-11-25 (superseded): headless-Chrome web tool dropped in favor of Overpass | FR-007, FR-012 | - |
| O6 | CLOSED 2026-11-24: stateless backend, criteria returned to the client | FR-014 | - |
| O9 | Moment times ("ce soir", "midi") and relevance rule: deferred by the team | FR-007, FR-008 | Team |
| O7 | Claude Design project not available to the author; FR-012 data shape not cross-checked against screens | FR-012 | Author |
| O8 | REQUIRED: the LLM endpoint must support tool calling (Overpass queries); also JSON mode; not verified, blocks the tool loop | R1, R2 | Developer |
| O11 | Overpass: public or local instance in each environment; bound values (timeout, response size, query length, calls per search); usage policy and rate limits of the public instances; disk, memory and build time of the local instance; (tool calling decided 2026-11-25) | FR-007, FR-012, FR-013 | Team |
| O12 | Validation of LLM-produced place coordinates and of the "in France" rule (which check, which tolerance): not defined; the LLM resolves places (decision 2026-11-25) | FR-006, FR-010, FR-015 | Team |
| O13 | OSM data completeness for FR-012 fields (hours, cuisine, diet, phone, photo, menu, signature dish): what is shown when a tag is absent | FR-012, FR-007 | Team |
| O14 | Fixture mode (`overpass.fixture-file`): development only, must never be active in production | - | Developer |
| O15 | How the device position reaches the search now that the LLM extracts the place: given to the LLM in the prompt, or substituted by the backend when no place is found | FR-006 | Team |
