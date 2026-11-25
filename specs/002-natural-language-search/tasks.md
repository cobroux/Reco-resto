# Tasks: Natural-Language Restaurant Search

**Input**: Design documents from `/specs/002-natural-language-search/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/search-api.openapi.yaml

**Tests**: REQUIRED (constitution II, Test-First; project rule "tests first"). In every phase, write the tests first and see them fail before the implementation.

**Organization**: grouped by user story. Paths are in `backend/` unless stated, package `hr.emocio.recoresto.search` (`P` below = `backend/src/main/java/hr/emocio/recoresto/search`, `T` = `backend/src/test/java/hr/emocio/recoresto/search`, `F` = `frontend/src/app/search`).

## Status of this task list

- Regenerated on 2026-11-25 after the pivot to Overpass (plan.md, research.md R2, R3): the LLM runs Overpass queries by tool calling, resolves places and extracts the criteria; the API Adresse, the headless-Chrome web tool and `CriteriaExtractor` are gone. The previous task list (2026-11-24) is replaced; its tasks T012-T013 (API Adresse), T014 (`CriteriaExtractorTest`), T019/T023 (web tool) are obsolete.
- `[x]` = the file and its test exist in the working tree and the task still holds after the pivot. **Nothing was re-run after the pivot**: the backend most likely does not compile (`LlmRestaurantSource` and `SearchService` still import deleted classes). T017 and T029 restore it.
- `spec.md` still says `Status: Draft`; the approving teammate sets it to `Approved` (specs/README.md). The plan has NOT been approved by a teammate yet (constitution I): do not start `/speckit-implement` before it is.
- **BLOCKED (Ox)**: the task depends on an open item of plan.md ("Open Items"). Do not fill the missing value with an assumption; get it from the team first. Blocked tasks do not stop the other tasks.
- Open items still unresolved: O3 (SC-001 threshold), O4 (SC-003 rate and reference set), O5 (privacy), O7 (Claude Design not cross-checked), O8 (tool calling on the LLM endpoint), O9 (moment times and relevance rule), O11 (Overpass instance and bounds), O12 (coordinates and "in France" validation), O13 (OSM data completeness), O14 (fixture mode off in production), O15 (how the device position reaches the search, see US2).
- Class names and packages below are proposals; confirm them at the plan review.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1, US2, US3

---

## Phase 1: Setup

- [x] T001 Check that `backend/pom.xml` exists (Java 21, Spring Boot 4.1.1).
- [x] T002 [P] Variable names (no values) in `.env.example`: `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`, and the optional `OVERPASS_*` overrides; check that `.env` is ignored by `.gitignore` (research.md R1, R2).
- [x] T003 [P] `backend/src/main/resources/application.yml`: `search.default-radius-km: 2.5`, `search.deck-size: 15` and `llm.*` read from the environment variables of T002.
- [x] T004 [P] Test `T/config/OverpassPropertiesTest.java`: `overpass.*` keys bind (`base-url`, `timeout-seconds`, `max-response-bytes`, `max-query-chars`, `max-calls`, `fixture-file`) and can be overridden; `fixture-file` is empty by default. Then add the `overpass.*` keys to `application.yml` (today `application.yml` has none and `.env.example` refers to defaults that do not exist). DONE 2026-11-25 with provisional values (O11 stays open): `max-calls` = 1 (given by the team; was 5 earlier the same day), and placeholders NOT validated: public instance `https://overpass-api.de/api/interpreter` (about 35 s per answer when tried, which exceeded the first 30 s timeout), timeout 120 s (raised from 30 s, then 60 s, at the request of the team), response 500000 bytes, query 2000 characters.

---

## Phase 2: Foundational (blocks all user stories)

- [x] T005 [P] Test `T/config/SearchPropertiesTest.java`: defaults 2.5 and 15 apply and can be overridden (FR-004, FR-008; data-model.md Configuration `search.default-radius-km`, `search.deck-size`).
- [x] T006 Implement `P/config/SearchProperties.java` (depends on T005).
- [x] T007 [P] Test `T/api/ApiExceptionHandlerTest.java`: each error code maps to the status of contracts/search-api.openapi.yaml (`NOT_UNDERSTOOD`, `NO_RESULT`, `OUTSIDE_FRANCE`, `PLACE_REQUIRED` → 422; `SERVICE_UNAVAILABLE` → 503) and the body has `code` and `message`.
- [x] T008 Implement `P/api/SearchErrorCode.java`, `P/api/SearchException.java`, `P/api/ApiExceptionHandler.java` (depends on T007).
- [x] T009 [P] Test `T/api/SearchRequestTest.java`: a blank or missing sentence is rejected (data-model.md `SearchRequest.sentence`); `position` is optional (data-model.md `SearchRequest.position`).
- [x] T010 Implement the records `SearchRequest`, `Position` (`P/api/`), `SearchCriteria` (`P/criteria/`), `Restaurant`, `SearchResult` (`P/restaurant/`), per data-model.md (`SearchCriteria.combination`, `SearchCriteria.moment`, `SearchCriteria.distanceLimit`, `Restaurant.address`, `SearchResult.deck`, `SearchResult.zone`) and the contract schemas (depends on T009).
- [x] T011 [P] Test `T/restaurant/OverpassClientTest.java` with a fake HTTP server: form `data=<query>` with a generic `User-Agent`; empty, too long or no `[out:json` query → `OverpassRefusal`; answer over the size limit or not JSON or without `elements` → `OverpassRefusal`; HTTP error or timeout → `SERVICE_UNAVAILABLE`; fixture mode replaces the call (research.md R2).
- [x] T012 Implement `P/restaurant/OverpassClient.java`, `P/config/OverpassProperties.java` (depends on T011). Number of calls per search (`maxCalls`) is enforced by T020, not here.
- [x] T013 [P] Test `T/llm/LlmChatClientTest.java` with a fake HTTP server: request `{model, messages}` to the chat-completions endpoint with the key in the authorization header; reply parsed; HTTP error or timeout → `SERVICE_UNAVAILABLE`.
- [x] T014 Implement `P/llm/LlmChatClient.java` using the configuration of T003, no key or URL in source (depends on T013).
- [x] T015 [P] Extend `T/llm/LlmChatClientTest.java` (fake server): tool-calling round trip: tools declared in the request, the assistant answers with a tool call, the client sends the tool result back and returns the final message; an answer that keeps calling tools is the caller's concern (T020).
- [x] T016 Extend `P/llm/LlmChatClient.java` in place with tool declaration and tool-call round trip (depends on T015). Do not add a second client.
- [x] T017 Check the real endpoint: tool calling works with the configured model, and note whether JSON mode is available (research.md R1). BLOCKED (O8): needs `LLM_BASE_URL` and `LLM_API_KEY`. If tool calling is not supported, STOP and ask the team (research.md R2 fallback). DONE 2026-11-25 with the `.env` values: HTTP 200, model `nova-lite`, `finish_reason: tool_calls` with a tool call, so tool calling is supported (O8 closed for the tool call itself; JSON mode not checked). `LLM_BASE_URL` has NO `/v1` suffix and `/chat/completions` works on it. Observed quirk: argument values come back HTML-escaped (`'1'` became `&#39;1&#39;`), which can corrupt Overpass queries: to handle.

**Checkpoint**: configuration, errors, models, Overpass client and LLM client with tools exist.

---

## Phase 3: User Story 1 - Get restaurants that match my sentence (P1) 🎯 MVP

**Goal**: a sentence returns understood criteria and matching restaurants, ranked, as a deck.

**Independent Test**: submit the US1 rows of quickstart.md with the LLM and Overpass faked, and check criteria and results.

### Tests (write first, they must fail)

- [x] T018 [P] [US1] `T/restaurant/SearchAnswerParserTest.java`: JSON final answer of the LLM → `SearchAnswer` (`understood`, `outsideFrance`, `criteria`, `centers`, `restaurants`). Criteria cases of the spec: scenario 1 (asian OR vegetarian, place "Opéra, Paris", moment now), scenario 2 (AND), mixed grouping "asiatique ou italien, et végétarien" (data-model.md `SearchCriteria.combination`), budget and dish dropped (FR-003), distance replaces default (data-model.md `SearchCriteria.distanceLimit`). Invalid items dropped; a missing field stays absent, never invented (data-model.md `Restaurant.address`); a reply that is not valid JSON or "blablabla" → not understood; a center with an invalid coordinate is rejected. The "in France" rule is not tested here (O12).
- [x] T019 [P] [US1] `T/restaurant/OverpassRestaurantSourceTest.java` (replaces `LlmRestaurantSourceTest`) with a fake `LlmChatClient` and a fake `OverpassClient`: the tool loop runs the query asked by the LLM and gives the result back; an `OverpassRefusal` message is given back to the LLM as the tool result, not thrown; the loop stops after `overpass.max-calls` calls; distance is computed by the backend from the center and the restaurant location, not trusted from the LLM (research.md R2); `SERVICE_UNAVAILABLE` propagates.
- [x] T020a [P] [US1] `T/criteria/MomentResolverTest.java`: no moment → now; explicit date-time (any coming day) kept; a moment in the past → `NOT_UNDERSTOOD` (data-model.md `SearchCriteria.moment`). The times for "ce soir" and "midi" are BLOCKED (O9): no test with an assumed hour.
- [x] T021 [P] [US1] `T/restaurant/RestaurantFiltersTest.java`: radius default and override (scenarios 3-5), open at the targeted moment from weekly hours including another day (FR-007), restaurant without address excluded (FR-016, data-model.md `Restaurant.address`).
- [x] T022 [P] [US1] `T/restaurant/DeckSelectorTest.java`: more matches than deck size → exactly the deck size (scenario 8, data-model.md `SearchResult.deck`); fewer → all (scenario 9); several places merged without duplicates (FR-015). Ordering by relevance BLOCKED (O9).
- [x] T023 [P] [US1] `T/api/SearchControllerTest.java`: `POST /api/search` matches the contract; the understood criteria are in every response (FR-014); `includeZone=true` adds `zone` (FR-013).
- [x] T024 [P] [US1] Update `T/SearchServiceTest.java` to the new flow (no extractor, no place resolver): the answer of the source gives criteria and centers; filters, deck and zone are applied; several centers merged (FR-015).

### Implementation

- [x] T025 [P] [US1] `P/criteria/MomentResolver.java` in Europe/Paris (inferred from "France only", to confirm with the team) (depends on T020a).
- [x] T026 [US1] Implement `P/restaurant/SearchAnswerParser.java` (depends on T018). Prompt text is in T027.
- [x] T027 [US1] Replace `P/restaurant/LlmRestaurantSource.java` in place by `P/restaurant/OverpassRestaurantSource.java` and change the port `P/restaurant/RestaurantSource.java` so that it takes the sentence and returns a `SearchAnswer`: French-language instructions, the Overpass tool declared to the LLM, the loop bounded by `overpass.max-calls`, results given back as data only (depends on T012, T016, T019, T026). Remove the `ApiAdresseClient` and `ResolvedPlace` imports. Needs T004 values for the bounds. DONE. Instructions are in English (project convention: everything in code is English), not French as written above; the model still receives French sentences. Bounds come from `OverpassProperties`, whose values are still missing (T004).
- [x] T028 [P] [US1] `P/restaurant/RestaurantFilters.java` (depends on T021).
- [ ] T029 [P] [US1] `P/restaurant/RelevanceRanker.java`. BLOCKED (O9): the relevance rule is not defined.
- [x] T030 [US1] `P/restaurant/DeckSelector.java` (limit from `SearchProperties`, merge, no duplicates) (depends on T006, T022, T028).
- [x] T031 [US1] Update `P/SearchService.java` in place: remove `CriteriaExtractor` and `PlaceResolver`, use the `SearchAnswer`, then moment, filters, ranking, deck, zone (depends on T024, T025, T027, T028, T030). The backend compiles and `mvn -f backend test` passes at this point. DONE: `mvn test` 99 tests pass.
- [x] T032 [US1] `P/api/SearchController.java` for `POST /api/search` with `includeZone` (depends on T008, T023). Re-check against T031.
- [x] T033 [P] [US1] Frontend: `F/http-search.service.ts` and `F/http-search.service.spec.ts` exist (the stub is deleted); check that `HttpSearchService` is the provider used by the app, that `frontend/proxy.conf.json` is wired in the `start` script, and that `npm --prefix frontend test -- --watch=false` passes. Spec 001 owns the home screen; which feature owns this service is to confirm with the team. DONE: `app.config.ts` provides `HttpSearchService` and `provideHttpClient()`, `search.service.ts` now emits a `SearchResult`, `angular.json` serves with `proxy.conf.json`, `app.config.spec.ts` added; 28 frontend tests pass. Not done: the home screen subscribes without an error handler, so a `SearchFailure` is not shown (spec 001 / frontend).

**Checkpoint**: US1 works with a sentence containing a place.

---

## Phase 4: User Story 2 - Use my position when I do not say where (P2)

**Goal**: without a place in the sentence, the position sent by the client is used; without both, no search runs.

**Independent Test**: submit a sentence without place, with then without `position`.

- [ ] T034 [P] [US2] `T/SearchServicePositionTest.java`: place in sentence → `position` not needed and not used (scenario 1); no place + position → position used (scenario 2); no place and no position → `PLACE_REQUIRED` and no Overpass query is run (scenario 3, FR-006). BLOCKED (O15): since the LLM extracts the place, it is not decided whether the position is given to the LLM in the prompt or substituted by the backend when `centers` is empty. Ask the team.
- [ ] T035 [US2] Extend `P/SearchService.java` (no parallel path) to apply T034 (depends on T031, T034).

Frontend part (asking the device for its position, "specify a place" message) is outside this task list: see T033 and plan.md.

---

## Phase 5: User Story 3 - Know what to do when the search fails (P3)

**Goal**: each failure returns its code (and the stated message) so the client can show the right message and action.

**Independent Test**: trigger each failure of quickstart.md (US3 rows).

- [x] T036 [P] [US3] `T/SearchFailureTest.java`: `NOT_UNDERSTOOD` (unintelligible sentence, invalid LLM reply); `NO_RESULT` (understood, no matching restaurant); `OUTSIDE_FRANCE` when the answer says so, with the message "Nappe n'est disponible qu'en France pour l'instant" (FR-010); `SERVICE_UNAVAILABLE` when the LLM or Overpass fails; the same request sent again behaves the same (stateless), so "Réessayer" just re-sends it.
- [x] T037 [US3] Implement the `NOT_UNDERSTOOD`, `NO_RESULT` and `OUTSIDE_FRANCE` paths in `P/SearchService.java` (extend, no duplicate error handling) (depends on T031, T036). The check that a place really is in France beyond the LLM's flag is BLOCKED (O12). DONE for the flags of the answer (`understood`, `outsideFrance`) and `NO_RESULT`; the extra France check stays BLOCKED (O12).
- [x] T038 [US3] Propagate `SERVICE_UNAVAILABLE` from `LlmChatClient` and `OverpassClient` through `ApiExceptionHandler` (depends on T008, T036).

The wording of the other messages and the "Réessayer" button are frontend; the backend returns codes only.

---

## Final Phase: Polish & Cross-Cutting

- [x] T050 [US1] 2026-11-25, at the request of the team: `origin/main` merged (it holds the swipe deck of spec 003) and the real search linked to it. `SearchService.search` returns the `DeckInput` of the deck; `F/deck-input.ts` (`toDeckInput`, with `F/deck-input.spec.ts`) converts the backend `SearchResult`: id = name + address, opening hours as 7 days indexed Sunday = 0, diets added to the keywords, restaurants without distance or address left out, target date-time = Paris time. `HttpSearchService` replaces `StubSearchService` (deleted); the deck refinement stays a stub (`StubRefinementService`: no refinement endpoint in spec 002). Not provided by the backend, so not shown: the moment label of the deck title ("ce soir", the title uses its default) and French labels for cuisines and diets (OpenStreetMap values are shown as they are). Backend `pom.xml` of `main` kept (springdoc), `@ConfigurationPropertiesScan` kept.
- [x] T049 [US1] 2026-11-25, at the request of the team (points B and D of the analysis of backend-live.log): (B) an HTTP 400 from Overpass (for example `Invalid regular expression: "^(?:asian)$"`) is no longer a `SERVICE_UNAVAILABLE`: `OverpassClient` gives it back to the model as a refusal with the message of Overpass and the hint that its regular expressions are POSIX extended (no `(?:...)`), without retry, and the prompt says the same; with `max-calls` at 1 the model cannot retry, so this only turns a 503 into `NO_RESULT`. (D) the model no longer gets the raw Overpass answer: `P/restaurant/OverpassAnswerReducer.java` keeps, for elements that have a name, type, id, coordinates and the tags `name`, `cuisine`, `diet:*`, `addr:*`, `phone`, `contact:phone`, `opening_hours`, `image` (chosen from the FR-012 fields, NOT validated: O13). Tests: `OverpassAnswerReducerTest`, `OverpassClientTest`, `OverpassRestaurantSourceTest`.
- [x] T048 [US1] 2026-11-25, at the request of the team: the Overpass tool refuses any filter whose key is exactly `name` (`OverpassQueryBuilder`; message to the model: filter by the `cuisine` tag, give places as coordinates), because the model used it for places and words of the sentence (see backend-live.log of 2026-11-25: `name="Asiatique Opéra"`, `name="Opéra"`). Other keys are unaffected. Consequence: no search by the name of a restaurant.
- [x] T047 [US1] 2026-11-25, at the request of the team: finding restaurants by type of cuisine (asian, italian...): the prompt of `OverpassRestaurantSource` tells the model to filter with the OpenStreetMap `cuisine` tag (English values, a regex matching one value inside a `;`-separated list such as `(^|;)(italian|pizza)(;|$)`, never a type of cuisine in a `name` filter). Checked once against the public Overpass: 3 restaurants found around the Opéra for italian|pizza, including `coffee_shop;italian`. Which OSM values make up a broad type such as "asiatique" is left to the model and NOT validated (O13).
- [x] T046 [US1] 2026-11-25, at the request of the team: `OverpassClient` retries a call after an HTTP 502 (public instance "at capacity") with waits of 5 s, 10 s, 20 s (4 attempts in all, `overpass.retry-delays-seconds`, env `OVERPASS_RETRY_DELAYS_SECONDS`); other errors are not retried. Tests in `OverpassClientTest` and `OverpassPropertiesTest`.
- [x] T045 [US1] 2026-11-25, at the request of the team (a filter requiring amenity=restaurant was added, rolled back, then restored the same day at the request of the team; Overpass timeout raised from 60 s to 120 s, provisional): the LLM sends its search as JSON in the format of the Overpass tag filters (`around`, `tags` with `value` or `regex`, optional `types`) and the backend builds the Overpass QL query: `T/restaurant/OverpassQueryBuilderTest.java` (14 tests) and `P/restaurant/OverpassQueryBuilder.java`, wired in `P/restaurant/OverpassRestaurantSource.java` (its tests updated). Step-by-step logs added in `SearchService`, `LlmChatClient`, `OverpassRestaurantSource` (INFO for the steps, DEBUG for the sentence, queries and raw LLM replies).
- [ ] T039 [P] Opt-in test `T/restaurant/ReferenceSentencesIT.java` running the reference sentences against the real LLM and a real or local Overpass (SC-003). BLOCKED (O4): target rate, set size and author.
- [ ] T040 [P] Measure the time from request to result (SC-001). BLOCKED (O3): threshold.
- [ ] T041 Security review of the Overpass tool: query bounds, number of calls, response size, prompt injection from OSM text (names, tags) given to the LLM as data, fixture mode never active in production (O14), no key or personal data in logs (research.md R2).
- [ ] T042 Record in the PR description the privacy validation of the LLM endpoint and of Overpass, public or local (O5), the Overpass instance chosen and its bounds (O11), and that the plan is not edited during implementation (constitution I).
- [ ] T043 Run the checks of quickstart.md; run `mvn -f backend test` and `npm --prefix frontend test -- --watch=false`.
- [ ] T044 DRY report in the PR. Reused: `SearchProperties`, `RestaurantFilters`, `DeckSelector`, `MomentResolver`, error classes. Extended: `LlmChatClient` (tools), `SearchService`, `RestaurantSource`. Replaced: `LlmRestaurantSource` by `OverpassRestaurantSource`, `StubSearchService` by `HttpSearchService`. Removed: `CriteriaExtractor`, `ApiAdresseClient`, `PlaceResolver`, `ResolvedPlace`, `GeocoderProperties` and their tests, and the `geocoder.*` keys of `application.yml`.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → user stories → Final Phase.
- T016 needs T015; T027 needs T012, T016, T019, T026; T031 needs T027 and restores the build.
- US1 first (MVP). US2 and US3 extend `SearchService` (T035, T037), so they start after T031.
- Inside a story: tests, then implementation.

## Parallel Examples

- Phase 2 tests: T005, T007, T009, T011, T013, T015.
- US1 tests: T018, T019, T020a, T021, T022, T023, T024.
- US1 implementation without shared files: T025, T028, T029, T033.

## Implementation Strategy

1. Phases 1 and 2, then US1 up to T031 with fakes for the LLM and Overpass, to get back to a compiling, green build first.
2. Close O8 and O11 as early as possible: they decide T004, T017 and the real quality of the results.
3. Add US2 (after O15), then US3, then the polish tasks once O3, O4, O9, O12 are answered.
