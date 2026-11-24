# Tasks: Natural-Language Restaurant Search

**Input**: Design documents from `/specs/002-natural-language-search/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/search-api.openapi.yaml

**Tests**: REQUIRED (constitution II, Test-First; project rule "tests first"). In every phase, write the tests first and see them fail before the implementation.

**Organization**: grouped by user story. Paths are in `backend/`, package `hr.emocio.recoresto.search` (`P` below = `backend/src/main/java/hr/emocio/recoresto/search`, `T` = `backend/src/test/java/hr/emocio/recoresto/search`).

## Status of this task list

- Generated on 2026-11-24 at the request of the team, who stated that spec and plan are approved. `spec.md` still says `Status: Draft`; the approving teammate sets it to `Approved` (specs/README.md).
- **BLOCKED (Ox)**: the task depends on an open item of plan.md ("Open Items"). Do not fill the missing value with an assumption; get it from the team first. Blocked tasks do not stop the other tasks.
- Open items still unresolved: O3 (SC-001 threshold), O4 (SC-003 rate and reference set), O5 (privacy), O7 (Claude Design not cross-checked), O8 (tool calling on the LLM endpoint), O9 (moment times and relevance rule), O10 (automation library, search engine, terms of use, deployment of Chrome).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1, US2, US3

---

## Phase 1: Setup

- [x] T001 Check that the backend setup chore is merged: `backend/pom.xml` exists (Java 21, Spring Boot 4.1.1) and `mvn -f backend test` passes. If not, STOP: the chore is not part of this feature (spec, Assumptions).
- [x] T002 [P] Add the variable names `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` (no values) to `.env.example`, and check that `.env` is ignored by `.gitignore` (research.md R1).
- [x] T003 [P] Add to `backend/src/main/resources/application.yml` the keys `search.default-radius-km: 2.5` and `search.deck-size: 15`, and the `llm.*` properties read from the environment variables of T002.

---

## Phase 2: Foundational (blocks all user stories)

- [x] T004 [P] Test `T/config/SearchPropertiesTest.java`: defaults 2.5 and 15 apply, and values can be overridden by configuration (FR-004, FR-008; data-model.md Configuration `search.default-radius-km`, `search.deck-size`).
- [x] T005 Implement `P/config/SearchProperties.java` (depends on T004).
- [x] T006 [P] Test `T/api/ApiExceptionHandlerTest.java`: each error code maps to the status of contracts/search-api.openapi.yaml (`NOT_UNDERSTOOD`, `NO_RESULT`, `OUTSIDE_FRANCE`, `PLACE_REQUIRED` → 422; `SERVICE_UNAVAILABLE` → 503) and the body has `code` and `message`.
- [x] T007 Implement `P/api/SearchErrorCode.java`, `P/api/SearchException.java` and `P/api/ApiExceptionHandler.java` (depends on T006).
- [x] T008 [P] Test `T/api/SearchRequestTest.java`: a blank or missing `sentence` is rejected (data-model.md `SearchRequest.sentence`); `position` is optional (data-model.md `SearchRequest.position`).
- [x] T009 Implement the records `SearchRequest`, `Position` (`P/api/`), `SearchCriteria` (`P/criteria/`), `Restaurant`, `SearchResult` (`P/restaurant/`), following data-model.md (`SearchCriteria.combination`, `SearchCriteria.moment`, `SearchCriteria.distanceLimit`, `Restaurant.address`, `SearchResult.deck`, `SearchResult.zone`) and the schemas of the contract (depends on T008).
- [x] T010 [P] Test `T/llm/LlmChatClientTest.java` with a fake HTTP server: request `{model, messages}` to `{LLM_BASE_URL}/v1/chat/completions` with the key in the authorization header; reply parsed; HTTP error or timeout → `SERVICE_UNAVAILABLE`; tool-call round trip (assistant requests a tool, client sends the tool result back). Tool-calling part BLOCKED (O8) until the endpoint is checked. DONE except the tool-calling part (O8).
- [x] T011 Implement `P/llm/LlmChatClient.java` using the configuration of T003, no key or URL in source (depends on T010).
- [x] T012 [P] Test `T/place/ApiAdresseClientTest.java` with a fake server: a place name resolves to coordinates; no result gives an empty answer distinct from a failure; HTTP error → `SERVICE_UNAVAILABLE`. Real behavior for places outside France and for landmark names such as "Opéra" must be checked against the real API (research.md R3). DONE with a fake server; real API still to check.
- [x] T013 Implement `P/place/ApiAdresseClient.java` and `P/place/PlaceResolver.java` (several places supported, FR-015) (depends on T012).

**Checkpoint**: configuration, errors, models, LLM client and geocoder exist.

---

## Phase 3: User Story 1 - Get restaurants that match my sentence (P1) 🎯 MVP

**Goal**: a sentence returns understood criteria and matching restaurants, ranked, as a deck.

**Independent Test**: submit the reference sentences of quickstart.md (US1 rows) with the LLM, geocoder and web tool faked, and check criteria and results.

### Tests (write first, they must fail)

- [x] T014 [P] [US1] `T/criteria/CriteriaExtractorTest.java`: scenario 1 (asian OR vegetarian, place "Opéra, Paris", moment now), scenario 2 (AND), mixed grouping "asiatique ou italien, et végétarien" (data-model.md `SearchCriteria.combination`), budget and dish dropped (FR-003), distance replaces default (`SearchCriteria.distanceLimit`), invalid LLM reply or "blablabla" → `NOT_UNDERSTOOD`.
- [x] T015 [P] [US1] `T/criteria/MomentResolverTest.java`: no moment → now; explicit date-time (any coming day) kept; a moment in the past → `NOT_UNDERSTOOD` (data-model.md `SearchCriteria.moment`). The times for "ce soir" and "midi" are BLOCKED (O9): no test with an assumed hour. DONE except the "ce soir"/"midi" times (O9).
- [x] T016 [P] [US1] `T/restaurant/RestaurantFiltersTest.java`: radius default and override (scenarios 3-5), open at the targeted moment from weekly hours including another day (FR-007), restaurant without address excluded from the deck (FR-016, data-model.md `Restaurant.address`).
- [x] T017 [P] [US1] `T/restaurant/DeckSelectorTest.java`: more matches than deck size → exactly the deck size (scenario 8, data-model.md `SearchResult.deck`); fewer → all (scenario 9); several places merged without duplicates (FR-015). Ordering by relevance BLOCKED (O9). DONE except relevance ordering (O9).
- [x] T018 [P] [US1] `T/restaurant/LlmRestaurantSourceTest.java`: JSON answer of the LLM parsed into restaurants; invalid items dropped; a missing field is left absent, never invented; distance computed from the geocoded address, not trusted from the LLM (research.md R2).
- [ ] T019 [P] [US1] `T/restaurant/WebBrowserToolTest.java`: only http/https allowed, private/local addresses refused, limits on pages, time and size per search; page text returned as data. BLOCKED (O10) for the automation library.
- [x] T020 [P] [US1] `T/api/SearchControllerTest.java`: `POST /api/search` matches contracts/search-api.openapi.yaml; the understood criteria are in every response (FR-014); `includeZone=true` adds `zone` (FR-013).

### Implementation

- [x] T021 [US1] Implement `P/criteria/CriteriaExtractor.java`: prompt in French, JSON reply validated against the SearchCriteria schema (depends on T009, T011, T014).
- [x] T022 [P] [US1] Implement `P/criteria/MomentResolver.java` using the Europe/Paris time zone (inferred from "France only", to confirm with the team) (depends on T015).
- [ ] T023 [P] [US1] Implement `P/restaurant/WebBrowserTool.java`: headless Chrome with the URL policy and limits of T019 (depends on T019). BLOCKED (O10).
- [x] T024 [US1] Implement the port `P/restaurant/RestaurantSource.java` and its LLM adapter `P/restaurant/LlmRestaurantSource.java` (LLM with the web tool, validation of the answer) (depends on T011, T013, T018, T023). DONE without the web tool (O10).
- [x] T025 [P] [US1] Implement `P/restaurant/RestaurantFilters.java` (radius, opening hours, address) (depends on T016).
- [ ] T026 [P] [US1] Implement `P/restaurant/RelevanceRanker.java`. BLOCKED (O9): the relevance rule is not defined.
- [x] T027 [US1] Implement `P/restaurant/DeckSelector.java` (limit from `SearchProperties`, merge, no duplicates) (depends on T005, T017, T025).
- [x] T028 [US1] Implement `P/SearchService.java` orchestrating extraction, place resolution, source, filters, ranking, deck and zone (depends on T021, T022, T024, T025, T027).
- [x] T029 [US1] Implement `P/api/SearchController.java` for `POST /api/search` with `includeZone` (depends on T007, T020, T028).

**Checkpoint**: US1 works with a sentence containing a place.

---

## Phase 4: User Story 2 - Use my position when I do not say where (P2)

**Goal**: without a place in the sentence, the position sent by the client is used; without both, no search runs.

**Independent Test**: submit a sentence without place, with then without `position`.

- [ ] T030 [P] [US2] `T/SearchServicePositionTest.java`: place in sentence → `position` not needed and not used (scenario 1); no place + position → position used (scenario 2); no place and no position → `PLACE_REQUIRED` and the restaurant source is never called (scenario 3, FR-006).
- [ ] T031 [US2] Extend `P/SearchService.java` (do not add a parallel path) to apply T030 (depends on T028, T030).

**Out of scope of this plan (to assign)**: asking the browser/device for the position and showing "specify a place" is frontend work; plan.md assigns the frontend to features 001/003/005. The team must confirm which feature owns it.

---

## Phase 5: User Story 3 - Know what to do when the search fails (P3)

**Goal**: each failure returns its code (and the stated message) so the client can show the right message and action.

**Independent Test**: trigger each failure of quickstart.md (US3 rows).

- [ ] T032 [P] [US3] `T/SearchFailureTest.java`: `NOT_UNDERSTOOD` (unintelligible sentence, invalid LLM reply); `NO_RESULT` (understood, no matching restaurant); `OUTSIDE_FRANCE` with the message "Nappe n'est disponible qu'en France pour l'instant" (FR-010); `SERVICE_UNAVAILABLE` when the LLM, the geocoder or the web tool fails; the same request sent again behaves the same (backend is stateless), so "Réessayer" just re-sends it.
- [ ] T033 [US3] Implement the `NOT_UNDERSTOOD`, `NO_RESULT` and `OUTSIDE_FRANCE` paths in `P/criteria/CriteriaExtractor.java`, `P/place/PlaceResolver.java` and `P/SearchService.java` (extend, no duplicate error handling) (depends on T028, T032). `OUTSIDE_FRANCE` detection BLOCKED (O2 verification) until API Adresse behavior is checked.
- [ ] T034 [US3] Propagate `SERVICE_UNAVAILABLE` from `LlmChatClient`, `ApiAdresseClient` and `WebBrowserTool` through `ApiExceptionHandler` (depends on T007, T032).

The wording of the other messages and the "Réessayer" button are frontend; the spec does not define their text and the backend returns codes only.

---

## Final Phase: Polish & Cross-Cutting

- [ ] T035 [P] Add the opt-in test `T/criteria/ReferenceSentencesIT.java` running the reference sentences against the real LLM (SC-003). BLOCKED (O4): target rate, set size and author.
- [ ] T036 [P] Measure the time from request to result (SC-001). BLOCKED (O3): threshold.
- [ ] T037 Security review of the web tool: URL policy, limits, prompt-injection from page text, no secret in logs (research.md R2).
- [ ] T038 Record in the PR description the privacy validation of the LLM endpoint, API Adresse and the web search (O5), and that the plan is not edited during implementation (constitution I).
- [ ] T039 Run the checks of quickstart.md; run `mvn -f backend test`.
- [ ] T040 DRY report in the PR: what was reused, extended or replaced (nothing existed in `backend/` before this feature, to re-check at implementation time).

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → user stories → Final Phase.
- US1 first (MVP). US2 and US3 extend `SearchService` (T031, T033), so they start after T028.
- Inside a story: tests, then models, then services, then controller.

## Parallel Examples

- Phase 2 tests: T004, T006, T008, T010, T012.
- US1 tests: T014 to T020.
- US1 implementation without shared files: T022, T023, T025, T026.

## Implementation Strategy

1. Phase 1 and 2, then US1 up to T029, using fakes for the LLM, geocoder and web tool.
2. Close O8 and O10 as early as possible: they decide T010, T019, T023 and the real quality of the results.
3. Add US2, then US3, then the polish tasks once O3, O4, O9 are answered.
