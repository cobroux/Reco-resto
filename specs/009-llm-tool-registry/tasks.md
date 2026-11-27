---

description: "Task list for the LLM tool registry and reviews tool"
---

# Tasks: LLM Tool Registry and Reviews Tool

**Input**: Design documents from `/specs/009-llm-tool-registry/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (`llm-tools.md`, `search-api-rating.openapi.yaml`), quickstart.md

**Tests**: Included and written first (constitution principle II: each test MUST fail before its implementation).

**Organization**: Grouped by user story. The reviews tool (US1, P1) is built on the registry (US2, P2), so the registry phase comes first (see Dependencies).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 (reviews tool and rating fields), US2 (registry)
- Backend paths are shortened: `MAIN` = `backend/src/main/java/hr/emocio/recoresto/search`, `TEST` = `backend/src/test/java/hr/emocio/recoresto/search`

## Phase 1: Setup and gates (human decisions)

**Purpose**: the constitution gates and the plan's open items. Nothing below is assumed by the agent.

- [X] T001 (human) Create the branch `feat/llm-tool-registry` from `main` (`git checkout -b feat/llm-tool-registry`); the earlier attempt in the session was denied.
- [ ] T002 (human) A teammate other than the author approves `specs/009-llm-tool-registry/spec.md` and `plan.md` and sets `Status: Approved` in the spec (constitution principle I).
- [ ] T003 (human) Decide plan open items 1-3 with the product owner: calls of `get_place_reviews` per sentence, maximum characters of one review, and whether `max-rounds` stays 3. Record the answers in `specs/009-llm-tool-registry/spec.md` (Clarifications) before approval. Blocks T026.
- [ ] T004 (human) Check in Google's documentation: (a) the terms on sending review text to a language model, (b) the billing category and the per-place review limit of a Place Details request with the `reviews` field. Record the result in `specs/009-llm-tool-registry/research.md` (R2, R5). Blocks T027 to T029.
- [ ] T005 (human) Confirm plan open item 6: with `google-places.fixture-file` set, `placeReviews` returns an empty list and logs a warning. Blocks T027.

---

## Phase 2: Foundational (blocking prerequisite)

**Purpose**: one shared JSON text helper, so the tools and the mapper do not each keep a copy (DRY).

**⚠️ CRITICAL**: no user story work starts before this phase is complete.

- [X] T006 Test then add `JsonAnswers.text(JsonNode)` (public static: trimmed string, or null when not a non-blank string, same logic as the private `text` of `SearchService`) in `MAIN/llm/JsonAnswers.java`; test in `TEST/llm/JsonAnswersTest.java` (create or extend).
- [X] T007 Replace the private `text(JsonNode)` copy of `GooglePlaceMapper` by `JsonAnswers.text` in `MAIN/google/GooglePlaceMapper.java`; `TEST/google/GooglePlaceMapperTest.java` keeps passing unchanged. Grep `MAIN` for any other identical copy and replace it too (the copy of `SearchService` goes in T014).

**Checkpoint**: `mvn -f backend test` passes.

---

## Phase 3: User Story 2 - Add a tool without rewriting the search (Priority: P2)

**Goal**: `search_restaurants` and its dispatch move into a registry, with no change in observable behavior (FR-001, FR-002, SC-001).

**Independent Test**: every `SearchServiceTest` case that existed before keeps its assertions and passes; an unknown tool and an over-limit call still get the same error text.

### Tests for User Story 2 (write first, see them fail)

- [X] T008 [P] [US2] `ToolRegistryTest` in `TEST/tool/ToolRegistryTest.java`: finds a tool by name, an unknown name gives empty, two tools with the same name fail at startup, `definitions()` lists every tool.
- [X] T009 [P] [US2] `SearchRestaurantsToolTest` in `TEST/tool/SearchRestaurantsToolTest.java`: the `runTool` and `forModel` cases now exercised through `SearchServiceTest` (radius clamp, unknown diets and prices dropped, query cap, unreadable or past `open_at` falls back to now, candidate ids `r1..rN`, an empty run does not replace a run that has candidates).
- [X] T010 [US2] Add to `TEST/SearchServiceTest.java` the cases unknown tool and over-limit call (same error text as today); do not change any existing assertion.

### Implementation for User Story 2

- [X] T011 [P] [US2] Create `LlmTool` (`name()`, `definition()`, `callLimit()`, `execute(String argumentsJson, ToolContext context)`) and `ToolContext` (`ToolContext.position`, `ToolContext.lastRun` of data-model.md, with the rule "replace the last run only when the new one has candidates, or when there is none yet") in `MAIN/tool/LlmTool.java` and `MAIN/tool/ToolContext.java`.
- [X] T012 [US2] Create `ToolRegistry` (Spring bean from all `LlmTool` beans, duplicate names rejected, `definitions()`, `find(name)`) in `MAIN/tool/ToolRegistry.java` (depends on T011).
- [X] T013 [US2] Create `SearchRestaurantsTool` in `MAIN/tool/SearchRestaurantsTool.java` by moving, without logic change, `TOOL_NAME`, `TOOLS` entry, `runTool`, `forModel`, `ToolRun`, `PRICE_LEVELS`, `DIETS`, `sorted` and `putIfPresent` out of `SearchService`; expose `run(String argumentsJson, Position position)` returning the `ToolRun`, used by `execute` and by the raw-sentence fallback of `SearchService` (depends on T011). Candidate rating and count are still read from the raw JSON here (replaced in T024).
- [X] T014 [US2] Refactor `SearchService.search` in `MAIN/SearchService.java`: send `registry.definitions()`, dispatch with `registry.find`, count calls per tool against `callLimit()`, read the last run from `ToolContext`, call `SearchRestaurantsTool.run` in the fallback at the old line 202; keep the loop, limits, fallbacks, error codes and the error texts. Delete every moved member and its private `text` copy in the same change (depends on T012, T013).
- [X] T015 [US2] Adapt the two `new SearchService(...)` calls of `TEST/SearchServiceTest.java` (old lines 67 and 271) to the new constructor; no assertion changes.
- [X] T016 [US2] Run `mvn -f backend test`: all pre-existing cases pass unchanged (SC-001).

**Checkpoint**: search behavior is identical and the registry is in place; a new tool can now be added by registering one bean.

---

## Phase 4: User Story 1 - Ratings and reviews on demand (Priority: P1) 🎯 MVP

**Goal**: the model can read a candidate's rating and at most 5 reviews; the answer carries `rating` and `ratingCount`, never review text (FR-003 to FR-007, SC-002, SC-003).

**Independent Test**: search "best rated sushi nearby" with a rated fixture place: a `get_place_reviews` call happens, the pick reason reflects the rating, the answer has `rating` and `ratingCount`, and no answer contains review text.

**Depends on**: Phase 3 (registry), T003 (settings values), T004, T005.

### Tests for User Story 1 (write first, see them fail)

- [ ] T017 [P] [US1] In `TEST/google/GooglePlaceMapperTest.java`: `Restaurant.rating` and `Restaurant.ratingCount` (data-model.md) are filled when the Google fields are numbers and absent otherwise.
- [ ] T018 [P] [US1] In `TEST/google/GooglePlacesClientTest.java`, `placeReviews`: path `/places/{placeId}`, headers `X-Goog-Api-Key` and `X-Goog-FieldMask: reviews`, `languageCode` from properties, at most `max-reviews` reviews, reviews without text skipped, 404 gives an empty list, 429 and 5xx follow D2, an id failing `GooglePlaceMapper.IDENTIFIER` sends no request, a missing key sends no request, fixture mode gives an empty list.
- [ ] T019 [P] [US1] `GetPlaceReviewsToolTest` in `TEST/tool/GetPlaceReviewsToolTest.java`, one case per row of the results table of `contracts/llm-tools.md`: rated restaurant, rated without review, no rating (FR-005), unknown id or no search yet, no `placeId`, Google failure is caught (SC-002), no author name in the result.
- [ ] T020 [P] [US1] In `TEST/api/SearchControllerTest.java`: the JSON of a rated restaurant has `rating` and `ratingCount`, one without rating has neither, and no answer contains review text (SC-003); update its `new Restaurant(` call (old line 72) in T022.
- [ ] T021 [US1] In `TEST/SearchServiceTest.java`: a search followed by a `get_place_reviews` call, the per-tool limit (the reviews limit does not consume the search limit), and a failing reviews call that still ends with a deck.

### Implementation for User Story 1

- [ ] T022 [US1] Add `Restaurant.rating` (`Double`) and `Restaurant.ratingCount` (`Integer`) from data-model.md at the end of the record in `MAIN/restaurant/Restaurant.java`; keep the 18-argument convenience constructor (both new values null) and update every caller of the canonical constructor: `withLocation`, `withReason`, and the test helpers `TEST/restaurant/TestRestaurants.java` (old line 26), `TEST/restaurant/DeckSelectorTest.java` (old lines 56 and 99), `TEST/api/SearchControllerTest.java` (old line 72).
- [ ] T023 [US1] In `MAIN/google/GooglePlaceMapper.java` fill `Restaurant.rating` and `Restaurant.ratingCount` from `rating` and `userRatingCount` (numbers only), calling the canonical constructor (depends on T022). No change to `SEARCH_FIELD_MASK`: both fields are already in it.
- [ ] T024 [US1] In `MAIN/tool/SearchRestaurantsTool.java`, `forModel` reads rating and count from `Restaurant.rating` and `Restaurant.ratingCount` and no longer from the raw JSON; the candidate keys `rating` and `reviews` stay as they are (depends on T023).
- [ ] T025 [P] [US1] Create the record `GoogleReview` with `GoogleReview.rating` and `GoogleReview.text` (data-model.md) in `MAIN/google/GoogleReview.java`.
- [ ] T026 [US1] Add `maxReviews`, `maxReviewCalls`, `maxReviewLength` (data-model.md "Settings") to `MAIN/config/ToolSearchProperties.java` and to the `search.tool` block of `backend/src/main/resources/application.yml`; `max-reviews` is 5, the other two take the values decided in T003 (depends on T003); update every place that constructs `ToolSearchProperties` (grep `TEST`).
- [ ] T027 [US1] Add `GooglePlacesClient.placeReviews(String placeId)` in `MAIN/google/GooglePlacesClient.java` as in the "Google Place Details request" table of `contracts/llm-tools.md`: reuse `call("reviews", ...)`, the header constants and `GooglePlaceMapper.IDENTIFIER`; cut each text to `maxReviewLength` (depends on T004, T005, T025, T026).
- [ ] T028 [US1] Create `GetPlaceReviewsTool` in `MAIN/tool/GetPlaceReviewsTool.java` (name `get_place_reviews`, argument `id`, `callLimit()` from `maxReviewCalls`, results exactly as in `contracts/llm-tools.md`, any `SearchException` caught, no author name) as a Spring bean (depends on T012, T024, T027).
- [ ] T029 [US1] Add the reviews step to `SYSTEM_PROMPT` in `MAIN/SearchService.java` (when to call `get_place_reviews`, and that the final reason may use the rating); the wording is reviewed by the team with `max-rounds` in mind (depends on T003, T004, T028).
- [ ] T030 [US1] Run `mvn -f backend test`: T017 to T021 now pass and nothing else regressed.
- [ ] T031 [US1] (human, with a real key) Run steps 3 and 4 of `quickstart.md`: ratings in the answer, a reviews call in the logs, and a deck still returned with an invalid key.

**Checkpoint**: both stories work; this is the full MVP.

---

## Phase 5: Polish and cross-cutting

- [ ] T032 [P] Grep `backend/src` for leftovers: `TOOL_NAME`, `runTool`, a private `text(JsonNode)`, and the raw `userRatingCount` read in `SearchService`; none may remain (DRY, constitution IV).
- [ ] T033 [P] Write the DRY report (reused, extended, replaced, as listed in plan.md "Reuse, extension and replacement") for the pull request description; the human opens the PR.
- [ ] T034 Run steps 1, 2 and 5 of `quickstart.md`, then the full `mvn -f backend test` (quality gate of the constitution; the frontend is untouched).

---

## Dependencies & Execution Order

- **Phase 1** gates: T001 first. T002 before T006 starts the implementation. T003 blocks T026; T004 blocks T027 to T029; T005 blocks T027. T003 to T005 can be answered in parallel.
- **Phase 2** (T006 then T007) blocks both stories.
- **US2 (Phase 3) before US1 (Phase 4)**: the spec ranks the reviews tool P1 and the registry P2, but the reviews tool is an `LlmTool` registered in the registry, so the registry is delivered first. US2 alone is shippable (pure refactor); US1 needs it.
- Within a story: tests, then code. Inside US2: T011 → T012 and T013 → T014 → T015 → T016. Inside US1: T022 → T023 → T024; T025 and T026 → T027 → T028 → T029 → T030.
- **Polish** after both stories.

### Parallel opportunities

- T008 and T009 together (different files); T011 can start with them once tests exist.
- T017, T018, T019, T020 together (four test files).
- T025 in parallel with T022 to T024.
- T032 and T033 together.

```text
# US1 tests together:
Task: T017 GooglePlaceMapperTest rating fields
Task: T018 GooglePlacesClientTest placeReviews
Task: T019 GetPlaceReviewsToolTest
Task: T020 SearchControllerTest rating, no review text
```

## Implementation Strategy

1. Clear the gates (T001 to T005; T004 and T005 can wait until T027).
2. Foundational helper (T006, T007).
3. US2 and validate: behavior unchanged (T016). This is a safe first commit (`refactor:`).
4. US1 and validate with real sentences (T031). Commit as `feat:`. Agents commit only when asked.
5. Polish (T032 to T034).

## Notes

- Spec and plan MUST NOT be edited during implementation; deviations go in the PR description (constitution principle I).
- Frontend and photos are out of scope (spec FR-008 and Assumptions).
- Do not commit `backend/.env` or any key.
