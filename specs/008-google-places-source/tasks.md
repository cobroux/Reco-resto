# Tasks: Google Places Restaurant Source

**Input**: Design documents from `/specs/008-google-places-source/` (spec.md, plan.md, research.md, data-model.md,
contracts/, quickstart.md)

**Prerequisites**: spec.md (Approved, test plan T1-T47 with every Expected filled), plan.md (Approved, decisions
D1-D21 in "Product owner decisions (2026-11-25)", D18/SC-004 included).

**Tests**: required (constitution II, spec test plan). Every behavior has a test task naming the spec test ids it
implements; the test is written first and MUST fail before the implementation task that follows it.

**Organization**: phases follow the user stories, split so that the backend source swap (US1-US4, backend part of
US2) is validated end to end with the **unchanged** frontend (FR-011) before any frontend change (US2 frontend part,
US5).

**Context constraints**

- Issue A is NOT merged on this branch: no `Elapsed`, no timing logs, no `LlmChatClient.ToolRefused`. No task
  depends on them; SC-001 is measured from the client side (plan, product owner decisions). Do not re-create a
  timing helper.
- Decision values used below come only from plan.md "Product owner decisions (2026-11-25)" (cited as D#) and
  research.md (cited as R#). Facts marked "to be confirmed by the team" in research.md stay so.
- T42 and T43 (maps of 005/006, FR-015) are deferred by the product owner: out of scope, no task.
- DRY (constitution IV): each implementation task reuses or extends the class named in the plan "DRY report";
  replaced Overpass/OSM code is removed in this feature (Phase 7).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1..US5 of spec.md
- Backend paths are relative to `backend/src/main/java/hr/emocio/recoresto/search/` (main) and
  `backend/src/test/java/hr/emocio/recoresto/search/` (test) unless written in full.

---

## Phase 1: Setup (configuration, environment, deployment)

**Purpose**: Google settings and the key variable exist everywhere before any code uses them.

- [x] T001 [P] Write `config/GooglePlacesPropertiesTest.java` (test): binds every key of data-model.md
  "Configuration" (`base-url`, `api-key`, `timeout-seconds`, `retry-delays-seconds`, `max-results`,
  `photo-max-width-px`, `language-code`, `region-code`, `fixture-file`), each `GOOGLE_PLACES_*` override, a blank
  `api-key` accepted at startup, an empty `retry-delays-seconds` meaning one attempt, and `toString()` never
  containing the key (FR-013). Follow the pattern of `config/LlmPropertiesTest.java`.
- [x] T002 Create `config/GooglePlacesProperties.java` (`@ConfigurationProperties("google-places")` record,
  registered like `LlmProperties`) and add the `google-places:` section to
  `backend/src/main/resources/application.yml`, each value overridable by its `GOOGLE_PLACES_*` variable:
  `base-url` `https://places.googleapis.com/v1`; `api-key` `${GOOGLE_PLACES_API_KEY:}` (no default value);
  `timeout-seconds` 10 (D1); `retry-delays-seconds` 1 (D2: one retry after 1 s); `max-results` 20 (D3, Nearby
  Search, no pagination: no `max-pages` key); `photo-max-width-px` 800 (D16); `language-code` fr, `region-code` FR
  (plan, technical); `fixture-file` empty. One comment per value citing its decision. Makes T001 pass.
- [ ] T003 [P] Add `GOOGLE_PLACES_API_KEY=` (empty value) to `.env.example`, with a one-line comment (server only,
  never committed with a value). The planner could not read this file; edit it by hand.
- [x] T004 [P] In `.github/workflows/deploy.yml`, step "Write the .env file": add
  `GOOGLE_PLACES_API_KEY: ${{ secrets.GOOGLE_PLACES_API_KEY }}` to `env:` and
  `GOOGLE_PLACES_API_KEY=%s\n` / `"$GOOGLE_PLACES_API_KEY"` to the `printf`, exactly like `LLM_API_KEY`. In
  `docs/deployment.md` "One-time setup", add the secret `GOOGLE_PLACES_API_KEY` next to the `LLM_*` line.
- [x] T005 [P] Create the development fixture `backend/dev/google-places-fixture.json`: a Nearby Search answer in
  the shape of research R4 with a few places, every value invented (marked NOT real data in a top-level comment
  field, as `overpass-fixture.json`, 002 O14). Also reused as recorded test data where convenient.

**Checkpoint**: `mvn -f backend test` passes; the application starts with an empty key.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: shared records and the single Google tables used by every story.

- [x] T006 [P] Extend the serialization checks of
  `api/SearchControllerTest.java` (test, T32 backend part, FR-011): a `Restaurant` with `website`, `placeId`,
  `photoAttributions`, `photoSourceUri`, `attributions` serializes them under those names; when absent they are
  omitted; every existing field keeps its name (contracts/search-api.openapi.yaml `Restaurant`).
- [x] T007 Extend `restaurant/Restaurant.java` with the optional fields of data-model.md `Restaurant.website`,
  `Restaurant.placeId`, `Restaurant.photoAttributions`, `Restaurant.photoSourceUri`, `Restaurant.attributions`
  (two small records `{displayName, uri}` and `{provider, providerUri}`); update every constructor call and
  `backend/src/test/java/hr/emocio/recoresto/search/restaurant/TestRestaurants.java`. Makes T006 pass.
- [x] T008 [P] Write `google/GooglePlaceTypesTest.java` (test, T3, T5): the restaurant check of D6 (`restaurant` and
  `*_restaurant` only: `cafe`, `bar`, `bakery` are not restaurants); the closed cuisine list (D7) equals the
  `*_restaurant` types of Google Table A (research R3); diet indicator types of D8 (`vegetarian_restaurant`,
  `vegan_restaurant`, `halal_restaurant`).
- [x] T009 Create `google/GooglePlaceTypes.java`: the single table of restaurant types (D6), the closed cuisine
  list (D7, copied from Google Table A, research R3; source URL in a comment) and the diet indicator types (D8).
  Makes T008 pass.
- [x] T010 [P] Create `google/GooglePlace.java` (internal record, never in the response) per data-model.md
  `GooglePlace.id`, `GooglePlace.types`, `GooglePlace.primaryType`, `GooglePlace.businessStatus`,
  `GooglePlace.servesVegetarianFood`, `GooglePlace.restaurant`.

**Checkpoint**: foundation ready; user stories can start.

---

## Phase 3: User Story 1 - Get my deck quickly (P1) - backend source swap, MVP

**Goal**: restaurants come from one Google Nearby Search per search point, built by the backend from the criteria;
the model makes one call without tools; 002 rules unchanged; response compatible with the current frontend.

**Independent Test**: `mvn -f backend test`, then with the unchanged frontend and a real key, "Je veux manger à
opera" returns a deck whose restaurants all have a `placeId` (T1), Overpass unreachable makes no difference (T2).

### Tests for User Story 1 (write first, must fail)

- [x] T011 [P] [US1] Write `google/GoogleOpeningHoursTest.java` (T23): Google day 0-6 → `SUNDAY`..`SATURDAY`;
  `HH:mm`; period within a day; period over midnight split into `(opens, "00:00")` and `("00:00", closes)`;
  open without close → `00:00`-`00:00` every day; empty `periods` treated as unknown hours (data-model.md
  "Opening hours mapping").
- [x] T012 [P] [US1] Write `google/GooglePlaceMapperTest.java` core fields (T1, T10, T23): per data-model.md
  `Restaurant.name` (place without name dropped), `Restaurant.location` (invalid coordinates → absent),
  `Restaurant.distanceMeters` (nearest search point, rounded), `Restaurant.cuisine` (`primaryTypeDisplayName.text`,
  French label, D21 (1)), `Restaurant.address` (`shortFormattedAddress`-style short address without ", France",
  D21 (2); absent stays absent), `Restaurant.openingHours` (absent when Google has none), `Restaurant.placeId`;
  `keywords`, `signatureDish`, `menu` always absent.
- [x] T013 [P] [US1] Write `google/GooglePlacesClientTest.java` search part with `MockRestServiceServer` (T1, T38,
  T39): `POST {base-url}/places:searchNearby`; headers `X-Goog-Api-Key` (header only, never in the URL) and
  `X-Goog-FieldMask` equal to the single field-mask constant (contracts/google-places-requests.md, using the
  short address field of D21 (2) and `nationalPhoneNumber` of D21 (3)); body `includedTypes`, `maxResultCount` 20,
  `locationRestriction.circle` (center, radius), `languageCode` fr, `regionCode` FR; 200 without `places` → empty
  list; any failure → `SearchException(SERVICE_UNAVAILABLE)` (refined in US4); fixture mode reads
  `google-places.fixture-file` and sends no request.
- [x] T014 [P] [US1] Write `criteria/CriteriaMatcherTest.java` cuisine part (T5, T3): AND/OR tree evaluated on a
  `GooglePlace`; a cuisine leaf matches when the place types contain that Google type (D7); no criteria matches
  every restaurant; unknown cuisine values were dropped before (data-model.md "Search criteria").
- [x] T015 [P] [US1] Write `google/GooglePlacesRestaurantSourceTest.java` (T1, T3, T4, T7, T45): one search per
  search point with the radius given; `includedTypes` built from the criteria only (cuisine types of the OR tree,
  else the restaurant types of D6) and never from model text; at most 50 included types (research R1) — if a
  criteria tree would exceed it, stop and ask; results of each point kept per point for `DeckSelector`; the number
  of places returned is never more than Google returned (FR-017).
- [x] T016 [P] [US1] Extend `restaurant/RestaurantFiltersTest.java` (T10, T23, SC-005): address required, radius,
  open at the moment, unknown hours kept (D9 (1), regular weekly hours D9 (2)); business status:
  `CLOSED_TEMPORARILY`, `CLOSED_PERMANENTLY`, `FUTURE_OPENING` excluded, `OPERATIONAL` and missing status kept
  (D9 (3)); places failing `CriteriaMatcher` excluded; non-restaurant types (D6) excluded.
- [x] T017 [P] [US1] Extend `restaurant/DeckSelectorTest.java` (T7, T11): the same `placeId` found around two
  places appears once, at its nearest distance; without `placeId` the current key is kept; deck size 15 and zone
  rule unchanged (D10: current order rule).
- [x] T018 [P] [US1] Write `criteria/SentenceInterpreterTest.java` happy path (T3, T4, T8, T20): one
  `LlmChatClient.complete(system, user)` call with no `tools` in the request; the system prompt lists the cuisine
  values from `GooglePlaceTypes` (single source) and mentions no Overpass or tag filter; a recorded answer with
  extra query/filter fields is read for its criteria only (T4); `outsideFrance=true` → outside France; 
  `understood=false` → not a request.
- [x] T019 [P] [US1] Extend `restaurant/SearchAnswerParserTest.java` for the new `SearchAnswer`: no `restaurants`;
  places as `NamedPlace(name, position)` (data-model.md `SearchAnswer.places`); states `UNDERSTOOD`,
  `NOT_A_REQUEST`, `OUTSIDE_FRANCE` (data-model.md `SearchAnswer.state`); existing valid answers still read the same.
- [x] T020 [US1] Rewrite `SearchServiceTest.java` 
  with fakes of the interpreter and the source (T1, T5, T6, T7, T8, T9, T11, T20, T38, T45):
  default radius 2.5 km (T5); "10 minutes à pied" → radius from `walking-speed-kmh` (T6); two places merged without
  duplicate (T7); outside France → `OUTSIDE_FRANCE` and the source is never called (T8); no place + device
  position → the device position is the only search point (D14, T9); no place and no position →
  `PLACE_REQUIRED`; deck of 15 and zone (T11, T45: zone ≤ what the source returned); empty source → `NO_RESULT`
  (T38); not a request → `NOT_UNDERSTOOD` (T20). Also: log lines contain counts, durations and place ids, never
  restaurant names or addresses (T44, plan technical choice).
- [x] T021 [US1] Reduce `llm/LlmChatClientTest.java`: delete the tool-calling tests (`Tool`, `ToolExecutor`, tool
  loop, `ROUNDS_AFTER_LIMIT`, `LIMIT_REACHED`); keep `complete(system, user)` and the empty-answer retry tests.

### Implementation for User Story 1

- [x] T022 [P] [US1] Create `google/GoogleOpeningHours.java` reusing `restaurant/OpeningPeriod`. Makes T011 pass.
- [x] T023 [US1] Create `google/GooglePlaceMapper.java` (core fields of T012, distance via
  `api/Position.distanceMetersTo`, citing data-model.md `Restaurant.*` fields). Makes T012 pass.
- [x] T024 [US1] Create `google/GooglePlacesClient.java` search operation (`RestClient` with connect/read timeouts
  from `GooglePlacesProperties`, field mask as one constant, fixture mode). Makes T013 pass.
- [x] T025 [P] [US1] Create `criteria/CriteriaMatcher.java` (cuisine leaves). Makes T014 pass.
- [x] T026 [US1] Change `restaurant/RestaurantSource.java` signature to a search from built criteria, search points
  and radius (plan DRY report) and create `google/GooglePlacesRestaurantSource.java` (requests per point in
  parallel, mapper, `GooglePlace` kept for filtering). Update every caller and test of the interface. Makes T015
  pass.
- [x] T027 [US1] Extend `restaurant/RestaurantFilters.java` (business status D9 (3), D6 type check, criteria match
  via `CriteriaMatcher`); keep the existing address/radius/open rules as is. Makes T016 pass.
- [x] T028 [US1] Extend `restaurant/DeckSelector.java` key: `placeId` when present. Makes T017 pass.
- [x] T029 [US1] Extend `restaurant/SearchAnswer.java` and `restaurant/SearchAnswerParser.java` (state, named
  places, no restaurants). Makes T019 pass.
- [x] T030 [US1] Create `criteria/SentenceInterpreter.java`: prompt moved from `OverpassRestaurantSource` and edited
  (no Overpass, no tag filters, cuisines from the closed list of `GooglePlaceTypes`, centers optional), one call
  without tools. Makes T018 pass.
- [x] T031 [US1] Reduce `llm/LlmChatClient.java`: remove `Tool`, `ToolExecutor`, the tool `complete` overload,
  `ROUNDS_AFTER_LIMIT`, `LIMIT_REACHED` (no caller left). Makes T021 compile and pass.
- [x] T032 [US1] Rewrite `SearchService.java` as the orchestrator of plan "Architecture" steps 1-6 (interpret,
  search points incl. device position D14, radius unchanged, source, filters, deck); logs without names or
  addresses. Makes T020 pass.
- [x] T033 [US1] Remove the Overpass search path in the same change (FR-001, D19, constitution IV):
  `restaurant/OverpassRestaurantSource.java`, `OverpassClient.java`, `OverpassQueryBuilder.java`,
  `OverpassAnswerReducer.java`, `OsmRestaurantMapper.java`, `OsmOpeningHours.java`, `config/OverpassProperties.java`
  and their tests (`OverpassRestaurantSourceTest`, `OverpassClientTest`, `OverpassQueryBuilderTest`,
  `OverpassAnswerReducerTest`, `OsmRestaurantMapperTest`, `OsmOpeningHoursTest`, `OverpassPropertiesTest`); the
  `overpass:` section of `backend/src/main/resources/application.yml`; `backend/dev/overpass-fixture.json`; the
  `OVERPASS_*` lines of `.env.example`; the folder `infra/overpass/` (D19). Then grep the repository (outside
  `specs/`) for `overpass` / `osm` and remove any remaining reference. Covers T2 (no Overpass code left).

**Checkpoint US1**: `mvn -f backend test` passes; `RecoRestoApplicationTests` context starts without any
`overpass` setting; curl with the fixture file returns a deck.

---

## Phase 4: User Story 2 (backend part) - richer card data and photos (P1)

**Goal**: the response carries photo (through the backend, key-free), hours, price level, website, phone, diets and
Google attributions.

**Independent Test**: mapper and photo endpoint tests; curl on the reference sentence shows the new fields and a
`photoUrl` of the form `/api/photos/{placeId}/{photoId}`.

### Tests for User Story 2 backend (write first, must fail)

- [x] T034 [P] [US2] Extend `google/GooglePlaceMapperTest.java` card fields (T21, T22, T24, T25, T29): data-model.md
  `Restaurant.priceLevel` per D5 (INEXPENSIVE €, MODERATE €€, EXPENSIVE €€€, VERY_EXPENSIVE €€€€, FREE and
  UNSPECIFIED/absent → absent); `Restaurant.website` from `websiteUri`, absent when missing (T25);
  `Restaurant.phone` national format (D21 (3)); `Restaurant.photoUrl` = `/api/photos/{placeId}/{photoId}` built
  from the first photo name, no key and no Google host (D16 a, T29), absent without photo (T22);
  `Restaurant.photoAttributions` from `photos[0].authorAttributions` (empty → absent); `Restaurant.photoSourceUri`
  from `photos[0].googleMapsUri`; `Restaurant.attributions` (empty → absent).
- [x] T035 [P] [US2] Extend `google/GooglePlaceMapperTest.java` and `criteria/CriteriaMatcherTest.java` diets
  (T26, T27, T28, FR-009): data-model.md `Restaurant.diets` per D8: vegetarian ← `servesVegetarianFood=true` or
  `vegetarian_restaurant`; vegan ← `vegan_restaurant`; halal ← `halal_restaurant`; `servesVegetarianFood=false` or
  absent never yields "not vegetarian"; a diet leaf is satisfied only when stated; under AND an unknown diet
  excludes the place; a diet Google never states (e.g. kosher) matches no place (T28 → `NO_RESULT` when nothing
  matches).
- [x] T036 [P] [US2] Extend `google/GooglePlacesClientTest.java` photo part (T29, T30, T39): `GET
  {base-url}/places/{placeId}/photos/{photoId}/media?maxWidthPx=800` with the key header only; image bytes and
  content type returned; Google 404 → not found; other failures → unavailable.
- [x] T037 [P] [US2] Write `api/PhotoControllerTest.java` (T29, T30, T39): `GET /api/photos/{placeId}/{photoId}`
  returns the image; path parameters outside `^[A-Za-z0-9_-]+$` → 400 without calling Google (no open proxy,
  research R5); unknown/expired photo → 404; Google failure or missing key → 503; the key never appears in the
  response headers or body.

### Implementation for User Story 2 backend

- [x] T038 [US2] Extend `google/GooglePlaceMapper.java` with price (D5 table as one constant), website, phone,
  photo address, attributions, diets (D8 table in `GooglePlaceTypes`). Makes T034 and the mapper part of T035 pass.
- [x] T039 [US2] Extend `criteria/CriteriaMatcher.java` with diet leaves (D8). Makes the matcher part of T035 pass.
- [x] T040 [US2] Add the photo operation to `google/GooglePlacesClient.java`. Makes T036 pass.
- [x] T041 [US2] Create `api/PhotoController.java` (contracts/search-api.openapi.yaml `/api/photos/{placeId}/{photoId}`).
  Makes T037 pass.

**Checkpoint US2 backend**: curl on the reference sentence shows the new fields; `/api/photos/...` serves an image.

---

## Phase 5: User Story 3 - Robust reading of the model answer (P1)

**Goal**: a well-understood sentence is never "not understood" because of the form of the answer (SC-003).

**Independent Test**: recorded faulty answers replayed through `SentenceInterpreter` / `SearchService`.

### Tests for User Story 3 (write first, must fail)

- [x] T042 [P] [US3] Add recorded faulty answers under `backend/src/test/resources/llm-answers/` (prose around the
  JSON, ```json fence, two JSON objects, only `understood` + place, no `centers`, out-of-range center, missing
  closing brace, empty), each for an understood sentence (SC-003 fixture set).
- [x] T043 [US3] Extend `restaurant/SearchAnswerParserTest.java` (T12, T13, T14, T15, T17, T18, T19): text around
  the answer read (T12); fence stripped (T13); two objects → the first complete object (D12, T14); missing optional
  fields → empty criteria, 002 defaults (T15); center outside lat/lng bounds → `NamedPlace` without position (T17);
  missing brace and empty answer → `UNREADABLE`, never `NOT_A_REQUEST` (T18, T19); braces inside strings handled.
- [x] T044 [P] [US3] Write `place/PlaceLocatorTest.java` with `MockRestServiceServer` (T16, T17, T8): a named place
  without position is located by Google Places Text Search (`places:searchText`, `textQuery` = name, `pageSize`
  1, mask `places.location,places.addressComponents`, same key header) (D13); no result → `PLACE_REQUIRED`; a
  located point outside France → `OUTSIDE_FRANCE`; failures → `SERVICE_UNAVAILABLE` (same mapping as US4).
- [x] T045 [US3] Extend `criteria/SentenceInterpreterTest.java` (T18, T19, D11): an `UNREADABLE` answer triggers a
  new call that shows the model its answer and asks for the JSON only, up to `llm.empty-answer-retries` (2) in all;
  still unreadable → `SearchException(SERVICE_UNAVAILABLE)`; a readable answer on retry continues normally.
- [x] T046 [US3] Extend `SearchServiceTest.java` (T16, T17, SC-003): places without position are located by
  `PlaceLocator` before the search; a valid model position is kept and not located (D13 (1)); every recorded
  answer of T042 that is understood ends in a deck, none in `NOT_UNDERSTOOD`.

### Implementation for User Story 3

- [x] T047 [US3] Extend `restaurant/SearchAnswerParser.java` (string-aware balanced-object scan, fence strip,
  first complete object, defaults, invalid center → no position, `UNREADABLE`). Makes T043 pass.
- [x] T048 [US3] Create `place/PlaceLocator.java` (Text Search through `google/GooglePlacesClient.java`, new
  `locate` operation; France check from `addressComponents`). Makes T044 pass.
- [x] T049 [US3] Extend `criteria/SentenceInterpreter.java` with the repair retry (D11), reusing
  `llm.empty-answer-retries` from `config/LlmProperties.java` (no new setting; update its comment in
  `application.yml` to "unusable answer"). Makes T045 pass.
- [x] T050 [US3] Wire `PlaceLocator` into `SearchService.java` step 2. Makes T046 pass.

**Checkpoint US3**: SC-003 fixture set: 0 "not understood".

---

## Phase 6: User Story 4 - Clear outcome when Google cannot answer (P2)

**Goal**: every Google failure → 503 `SERVICE_UNAVAILABLE` ("Réessayer"), cause logged without the key.

**Independent Test**: `GooglePlacesClientTest` failure cases and `SearchControllerTest` 503.

### Tests for User Story 4 (write first, must fail)

- [x] T051 [P] [US4] Extend `google/GooglePlacesClientTest.java` failures (T33, T34, T35, T36, T37, T39): connection
  error and 5xx (T33), read timeout (T34), 429 `RESOURCE_EXHAUSTED` (T35), blank key → no request sent (T36), 400
  `API_KEY_INVALID` (T37), 403, unreadable JSON → `SERVICE_UNAVAILABLE` each; one retry after 1 s on 5xx, 429 and
  timeout only (D2; inject the sleeper to keep the test fast); captured WARN log names the operation and the cause
  class of contracts/google-places-requests.md (`key-missing`, `key-invalid`, `permission-denied`, `quota`,
  `unavailable`, `timeout`, `bad-answer`) and never contains the key value or request headers.
- [x] T052 [P] [US4] Extend `config/GooglePlacesPropertiesTest.java` or add a startup test (T36): a blank key logs
  one WARN at startup and the application still starts.
- [x] T053 [P] [US4] Extend `api/SearchControllerTest.java` (T33-T37, T38): a source failure gives HTTP 503
  `{"code":"SERVICE_UNAVAILABLE"}`; an empty Google answer gives the 002 `NO_RESULT` status.

### Implementation for User Story 4

- [x] T054 [US4] Implement in `google/GooglePlacesClient.java` the error mapping, retry (D2) and log lines (cause
  only, never key or headers), shared by search, locate and photo. Makes T051 pass.
- [x] T055 [US4] Add the startup WARN for a blank key in `config/GooglePlacesProperties.java` (or the client
  constructor, as `LLM_API_KEY` is handled). Makes T052 pass; T053 passes with `ApiExceptionHandler` unchanged.

---

## Phase 7: Backend validation checkpoint (before any frontend change)

**Purpose**: validate the source swap with the **unchanged** frontend (FR-011).

- [ ] T056 Run `mvn -f backend test` (all green) and `npm --prefix frontend test -- --watch=false` (unchanged
  frontend, all green).
- [ ] T057 Manual run with a real key and the unchanged frontend (quickstart.md "Run end to end"): T1 (every
  restaurant has a `placeId`), T2 (no Overpass anywhere), T3/T5-T11/T38/T45 sentences with curl, T31 (deck opens
  with the current frontend), SC-005 check. Record the outcomes in the PR.
- [ ] T058 Timing and reliability run (T46, T47, SC-001, SC-002): "Je veux manger à opera" 3 times in the browser,
  time from "Commencer" to the first card from the client side (browser performance panel or request duration; no
  backend timing logs on this branch); report each run, the mean (target < 20 s) and 3/3 decks. Requires the real
  key and the Google quotas of T068 set first.
- [ ] T059 SC-004 measurement: on 3 runs of "Je veux manger à opera", count the deck cards with a `photoUrl`, with
  `openingHours` and with a `priceLevel`; target set by the product owner: at least 80 % of the deck cards have a
  photo, opening hours and a price level (spec SC-004). Report the share of cards having all three and each share
  separately (the spec sentence can be read either way; the product owner confirms which one is the criterion).
  Amended 2026-11-26: the criterion is opening hours and price level all at once (Google returns no photo for this
  project; the photo is measured by spec 007). Use "Je veux manger à opera ce midi" when run in the morning (the
  "open at the moment" filter of 002 keeps almost no place before lunch).

**Checkpoint**: backend accepted; frontend work may start.

---

## Phase 8: User Story 2 (frontend part) - richer cards in the browser (P1)

**Goal**: the frontend reads the new optional fields and falls back to the tablecloth when a photo fails.

**Independent Test**: `npm --prefix frontend test -- --watch=false`.

### Tests for User Story 2 frontend (write first, must fail)

- [ ] T060 [P] [US2] Extend `frontend/src/app/search/deck-input.spec.ts` (T31, T32, T21): a response with
  `website`, `placeId`, `photoAttributions`, `photoSourceUri`, `attributions` maps without error; the deck id uses
  `placeId` when present (research R11), else `name|address`; a response without them maps as today.
- [ ] T061 [P] [US2] Extend `frontend/src/app/deck/swipe-card/swipe-card.component.spec.ts` (T21, T22, T23, T24,
  T30): photo shown when `photoUrl` is set; an `error` event on the `<img>` shows the tablecloth (FR-010, T30); no
  photo → tablecloth (T22); opening tile hidden without hours (T23) and no price chip without price (T24) still hold.

### Implementation for User Story 2 frontend

- [ ] T062 [P] [US2] Add the optional fields to `frontend/src/app/search/search-result.ts` and
  `frontend/src/app/restaurant/restaurant.ts` (contracts/search-api.openapi.yaml `Restaurant`), map them in
  `frontend/src/app/search/deck-input.ts`. Makes T060 pass.
- [ ] T063 [US2] Add the image error fallback in `frontend/src/app/deck/swipe-card/swipe-card.component.html` and
  `swipe-card.component.ts`. Makes T061 pass.

---

## Phase 9: User Story 5 - Google Maps Platform terms (P2)

**Goal**: attribution on every screen showing Google data without a Google map (FR-014); nothing kept beyond the
terms (FR-016). Maps (FR-015, T42, T43) are deferred: out of scope.

**Independent Test**: swipe-card spec; review of storage and logs.

### Tests for User Story 5 (write first, must fail)

- [ ] T064 [P] [US5] Extend `frontend/src/app/deck/swipe-card/swipe-card.component.spec.ts` (T40, T41, SC-007):
  the card front shows the text "Google Maps" on the photo area with `translate="no"` (D15, research R6), also on a
  card with the tablecloth; when `photoAttributions` is present, the author name(s) appear in small text under it,
  linked to `uri`; when `attributions` (data providers) is present, they are shown with "Google Maps".

### Implementation for User Story 5

- [ ] T065 [US5] Implement the attribution in `swipe-card.component.html` / `.ts` and its style (discreet text,
  D15; the "Google Maps" text is a frontend constant, not translated). Makes T064 pass. Screens of 005 and 006 are
  not touched (FR-015 deferred).
- [ ] T066 [US5] Review for T44 (FR-016): confirm no file, database or browser storage receives Google content
  (search `localStorage`, `sessionStorage`, `IndexedDB`, file writes in backend and frontend), and backend logs
  contain place ids and counts only (covered by T020); record the outcome in the PR. The product owner checks
  T44 (spec: judgment test).

---

## Phase 10: Polish & cross-cutting

- [ ] T067 [P] Key exposure review (T39, SC-006, FR-013): with the real key set, grep the backend logs, search
  responses, `photoUrl` values, the built frontend assets (`npm --prefix frontend run build` output) and the
  browser network panel for the key value and for `key=`: 0 occurrences. Record in the PR.
- [ ] T068 [P] Document in `docs/deployment.md` (new section "Google Places quotas", linked from `quickstart.md`
  "Prerequisites") the daily quota caps the product owner sets in the Google Cloud console (APIs & Services >
  Places API (New) > Quotas) so that usage stays within the free monthly caps and Google never bills (product owner
  decision, plan "Billing"): one cap per method used — `SearchNearby` (Nearby Search Enterprise + Atmosphere,
  1,000 free/month), `SearchText` (place location of D13; its SKU depends on the mask
  `places.location,places.addressComponents`, to be confirmed on the pricing page), `GetPhotoMedia` (Place Details
  Photos, 1,000 free/month) — derived as free monthly cap ÷ 31 rounded down (e.g. 1,000 → 32 per day); state that
  retries (D2) count as requests, that requests beyond the cap are refused by Google and give "service unavailable"
  (FR-012), that budget alerts alone do not stop billing, and that the free caps come from research R2 and are to
  be confirmed by the product owner against the current pricing page before setting the caps.
- [ ] T069 [P] Update `specs/008-google-places-source/quickstart.md` prerequisites: the quotas of T068 set before
  any real run; Google fixture file name `backend/dev/google-places-fixture.json`.
- [ ] T070 Final run: `mvn -f backend test` and `npm --prefix frontend test -- --watch=false` green; quickstart.md
  scenarios replayed; PR description lists deviations, the DRY report (reused/extended/replaced/removed) and the
  judgment tests to be checked by the product owner (T14, T27, T28, T44).

---

## Dependencies & Execution Order

### Phase dependencies

- Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1) → Phase 4 (US2 backend) and Phase 5 (US3) and Phase 6
  (US4) → Phase 7 (backend checkpoint) → Phase 8 (US2 frontend) and Phase 9 (US5) → Phase 10.
- Phases 4, 5 and 6 depend on US1 (client, mapper, interpreter, service) but not on each other, except shared
  files (see below); run them sequentially if one developer, or in parallel taking care of shared files.
- T058 (timing run) needs T068 (quotas documented and set) and a real key.
- T068 can be written at any time after Phase 1.

### Shared files (sequence, not parallel)

- `google/GooglePlacesClient.java`: T024 → T040 → T048 (locate) → T054.
- `google/GooglePlaceMapper.java`: T023 → T038. `criteria/CriteriaMatcher.java`: T025 → T039.
- `SearchService.java`: T032 → T050. `restaurant/SearchAnswerParser.java`: T029 → T047.
- `swipe-card.component.*`: T063 → T065.

### Critical path

T001 → T002 → T007 → T009 → T013 → T024 → T015 → T026 → T020 → T032 → T033 → (T051 → T054) → T056 → T057 →
T068 (quotas set) → T058 → T060/T061 → T063 → T064 → T065 → T070.

## Parallel examples

```text
Phase 1: T001, T003, T004, T005 together.
Phase 2: T006, T008, T010 together.
US1 tests: T011, T012, T013, T014, T015, T016, T017, T018, T019 together; then T022 and T025 in parallel.
US2 backend tests: T034, T035, T036, T037 together.
US3: T042 and T044 together.
US4: T051, T052, T053 together.
Frontend: T060, T061 together; T064 after T063.
Polish: T067, T068, T069 together.
```

## Implementation strategy

1. MVP = Phases 1-3: Google replaces Overpass behind the unchanged contract; validate with the fixture file and
   the unchanged frontend.
2. Add US2 backend (card data, photos), US3 (robust reading), US4 (failures); run Phase 7 with a real key:
   SC-001, SC-002, SC-004, SC-005 measured before any frontend change.
3. Add the frontend (US2 photo fallback, US5 attribution), then Polish.

## Spec test coverage

| Spec tests | Tasks |
|---|---|
| T1 | T012, T013, T015, T020, T057 |
| T2 | T033, T057 |
| T3 | T008, T014, T015, T018, T057 |
| T4 | T015, T018 |
| T5 | T008, T014, T020, T057 |
| T6 | T020, T057 |
| T7 | T015, T017, T020, T057 |
| T8 | T018, T020, T044, T057 |
| T9 | T020, T057 |
| T10 | T012, T016, T057 |
| T11 | T017, T020, T057 |
| T12-T15 | T043 (T042 fixtures) |
| T16, T17 | T043 (T17), T044, T046 |
| T18, T19 | T043, T045 |
| T20 | T018, T020 |
| T21 | T034, T060, T061 |
| T22 | T034, T061 |
| T23 | T011, T012, T016, T061 |
| T24 | T034, T061 |
| T25 | T034 |
| T26-T28 | T035 |
| T29 | T034, T036, T037 |
| T30 | T036, T037, T061 |
| T31 | T057, T060 |
| T32 | T006, T060 |
| T33-T37 | T051, T052 (T36), T053 |
| T38 | T013, T020, T053, T057 |
| T39 | T013, T036, T037, T051, T067 |
| T40, T41 | T064 |
| T42, T43 | deferred (FR-015, product owner): no task |
| T44 | T020 (logs), T066 |
| T45 | T015, T020, T057 |
| T46, T47 | T058 |
| SC-003 | T042, T046 |
| SC-004 | T059 |
| SC-005 | T016, T057 |
| SC-006 | T067 |
| SC-007 | T064 |

## Notes

- [P] = different files, no dependency on an incomplete task. Shared files are sequenced above.
- Every test task must fail before its implementation task; commit only when asked (agents never push).
- If a needed value is not in plan.md "Product owner decisions" or research.md, stop and ask (constitution III).
