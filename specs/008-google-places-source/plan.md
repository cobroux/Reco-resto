# Implementation Plan: Google Places Restaurant Source

**Branch**: `feat/google-places-source` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-google-places-source/spec.md`

**Status**: Draft, to be reviewed. Twenty-one business decisions are open (see "Open decisions"); `/speckit-tasks`
MUST NOT start before they are closed, and `/speckit-implement` MUST NOT start while the Expected column of the
spec's test plan is empty.

## Summary

The language model no longer searches restaurants. It is called once, without tools, and only returns what it
understood from the sentence (criteria, AND/OR tree, places with their coordinates when it knows them, moment,
distance or time on foot, outside France, not a request). The backend reads that answer robustly (text or code
fence around the JSON, missing optional fields, missing positions, unreadable answer recovered by a new attempt),
locates the places that have no usable position, builds the Google Places API (New) requests itself from the
criteria (one search per place), maps every Google place to the existing `Restaurant` record, then applies the
existing filters (address, radius, open at the targeted moment) extended with the AND/OR criteria match, and cuts
the deck with the existing `DeckSelector`. The response of `POST /api/search` only gains optional fields (website,
place identifier, photo and data attributions). The Google API key stays on the server: photos reach the browser
through a backend address that never contains the key (exact mechanism: open decision D16).

Everything specific to Overpass and OpenStreetMap is removed in the same change (FR-001, constitution IV).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript / Angular 22 (frontend), both existing.

**Primary Dependencies**: Spring Boot 4.1.1 (`RestClient`, `@ConfigurationProperties`, Jackson 3 `tools.jackson`),
existing OpenAI-compatible LLM endpoint (`LlmChatClient`, model `nova-lite`), Google Places API (New) over HTTPS
(`places.googleapis.com/v1`, Text Search or Nearby Search, Place Photos). No new library: the Google client is a
plain `RestClient`, like `OverpassClient` and `LlmChatClient` today (convention of the code base).

**Storage**: none. The backend stays stateless (002 R6). Caching of Google content: open decision D4.

**Testing**: JUnit (`mvn -f backend test`), mirroring packages; Google and the LLM faked with `MockRestServiceServer`
and recorded JSON (convention of `OverpassClientTest`, `LlmChatClientTest`). Recorded faulty LLM answers for
SC-003 (T12-T19). Frontend: Karma/Jasmine `*.spec.ts` next to the code. One opt-in run against the real services for
SC-001/SC-002 (quickstart.md).

**Target Platform**: Linux server (Docker on the EC2 instance, `docker-compose.yml`) behind nginx; Angular SPA.

**Project Type**: web application (backend REST API + Angular frontend) in one monorepo.

**Performance Goals**: SC-001: mean of 3 runs of "Je veux manger à opera" under 20 s from "Commencer" to the first
card. Budget estimate (not measured for Google): 1 LLM call (~1 s measured 2026-11-25) + 1 Google search per place
and per page (latency NOT measured, to be measured at T46) + photo loading by the browser (does not delay the first
card's data). Google timeout: D1.

**Constraints**: API key only in the backend environment variable `GOOGLE_PLACES_API_KEY` (FR-013); never logged,
never in a response or a URL given to the browser; Google Maps Platform terms (attribution FR-014, EEA map rule
FR-015, caching FR-016); response contract backward compatible (FR-011); France only (002 FR-010): no Google request
when the model says the place is outside France.

**Scale/Scope**: POC, 4 contributors. Free monthly caps (research R4): 1,000 Enterprise(+Atmosphere) searches and
1,000 photos; budget: D17.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|-----------|--------|------|
| I. Spec-Driven Development | Pass, with exception | One branch, one `specs/008-...` folder. The spec was self-approved by the product owner for the POC (exception recorded in spec.md). This plan MUST be approved by a teammate other than its author before `/speckit-tasks`. Specs 002, 003, 005, 006, 007 are not edited. |
| II. Test-First | Pass (to enforce) | Tasks MUST put every test before its code; the recorded LLM answers and Google answers are test fixtures written first. |
| III. Never Guess | **Open** | 21 business decisions listed below (D1-D21) are not filled with assumed values. Where a configuration key needs a value, the plan names the key and leaves the value to the decision. Facts from Google documentation are cited in research.md, uncertain ones are marked. |
| IV. DRY | Pass | Reuse / extend / replace / remove list below. One authoritative place per table (price mapping, cuisine types, diet indicators, field mask). |
| V. English and Conventional Commits | Pass | |

Post-design re-check (after Phase 1): unchanged. The design introduces no new project, no new library and no
persistence. Gate III stays open until D1-D21 are answered.

### DRY report (reuse, extend, replace, remove)

| Existing code | Fate | Why |
|---|---|---|
| `SearchService` | Extended | Becomes the orchestrator: interpret, locate, search per place, filter, deck. Uses the device position (D14). |
| `RestaurantSource` (port) | Extended (signature) | `find(String sentence)` becomes a search from built criteria and search points; the single implementation and every test are updated. |
| `OverpassRestaurantSource` | Replaced, removed | Its prompt moves (edited) to `SentenceInterpreter`; its search role goes to `GooglePlacesRestaurantSource`. |
| `LlmChatClient` | Extended and reduced | Plain `complete(system, user)` kept; empty-answer retry generalized to "unusable answer" (D11). Tool calling (`Tool`, `ToolExecutor`, tool loop, `ROUNDS_AFTER_LIMIT`, `LIMIT_REACHED`, and `ToolRefused` from issue A) has no caller left and is removed. |
| `SearchAnswerParser` | Extended | Robust JSON extraction (FR-004), defaults (FR-005), places without positions kept as names (FR-006), new "unreadable" outcome distinct from "not understood" (FR-007). |
| `SearchAnswer` | Extended | Loses `restaurants` (the model never gave any); gains the unreadable state and named places. |
| `SearchCriteria`, `CriteriaNode`, `MomentResolver` | Reused as is | Defaults already handled (null lists, null/"now" moment). |
| `RestaurantFilters` | Extended | Same address / radius / open rules; adds the AND/OR criteria match (D7, D8) and the Google business status. |
| `OpeningPeriod`, `Restaurant`, `SearchResult` | Reused; `Restaurant` extended | Optional fields added (contract). |
| `DeckSelector` | Extended | Deduplication key becomes the place identifier when present (same Google place found around two places, FR-003 / 002 FR-015). Order: D10. |
| `SearchErrorCode`, `SearchException`, `ApiExceptionHandler` | Reused as is | Every Google failure is `SERVICE_UNAVAILABLE` (FR-012 option A). |
| `Position.distanceMetersTo` | Reused | Distance to the nearest search point (moved out of `OsmRestaurantMapper.nearestDistance`). |
| `OverpassClient` | Replaced by `GooglePlacesClient`, removed | Same patterns kept: `RestClient` with timeouts, failure → `SERVICE_UNAVAILABLE`, optional development fixture file. |
| `OsmRestaurantMapper`, `OsmOpeningHours` | Replaced by `GooglePlaceMapper`, `GoogleOpeningHours`, removed | |
| `OverpassQueryBuilder`, `OverpassAnswerReducer`, `OverpassProperties`, `overpass:` in `application.yml`, `backend/dev/overpass-fixture.json` and their tests | Removed | No caller left (FR-001). |
| `Elapsed` and the timing logs (issue A, not merged here) | Reused if issue A is merged first | Needed to measure SC-001; see "Merge note". |
| `infra/overpass` | D19 | Out of scope of the spec ("plan and cleanup decision"); not removed without the team. |
| Frontend `search-result.ts`, `deck-input.ts` | Extended | New optional fields read; nothing removed. |
| Frontend `swipe-card` | Extended | Photo load failure → tablecloth (FR-010); Google attribution (FR-014, form D15). |

## Project Structure

### Documentation (this feature)

```text
specs/008-google-places-source/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0: Google facts (cited), technical decisions, open decisions
├── data-model.md        # Phase 1: entities, mapping rules, states of the model answer
├── quickstart.md        # Phase 1: validation guide
├── contracts/
│   ├── search-api.openapi.yaml       # POST /api/search (002 contract + optional fields) and the photo address
│   └── google-places-requests.md     # Outbound contract: Google requests, field mask, error mapping
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks, not created here
```

### Source Code (repository root)

```text
backend/src/main/java/hr/emocio/recoresto/search/
├── SearchService.java                     # extended: orchestration, device position, timing logs
├── api/
│   ├── SearchController.java              # unchanged
│   ├── PhotoController.java               # NEW (if D16 = backend photo address): GET /api/photos/...
│   └── ...                                # SearchErrorCode, SearchException, ApiExceptionHandler unchanged
├── config/
│   ├── GooglePlacesProperties.java        # NEW: google-places.* (key from GOOGLE_PLACES_API_KEY)
│   ├── LlmProperties.java                 # extended (D11)
│   └── OverpassProperties.java            # REMOVED
├── criteria/
│   ├── SentenceInterpreter.java           # NEW: one LLM call, prompt moved from OverpassRestaurantSource
│   ├── CriteriaMatcher.java               # NEW: evaluates the AND/OR tree on a place (D7, D8)
│   └── SearchCriteria, CriteriaNode, MomentResolver (unchanged)
├── llm/LlmChatClient.java                 # reduced: tool calling removed, unusable-answer retry
├── place/
│   └── PlaceLocator.java                  # NEW: positions for named places without one (D13)
├── google/                                # NEW package (name to confirm at review)
│   ├── GooglePlacesClient.java            # HTTP: search, photo; errors → SERVICE_UNAVAILABLE; fixture mode
│   ├── GooglePlacesRestaurantSource.java  # implements RestaurantSource: builds requests from criteria
│   ├── GooglePlaceMapper.java             # Google place → Restaurant (price table D5, diets D8)
│   ├── GoogleOpeningHours.java            # periods → Map<DAY, List<OpeningPeriod>>
│   └── GooglePlaceTypes.java              # single table: restaurant types (D6), cuisine → types (D7)
└── restaurant/
    ├── RestaurantSource.java              # signature extended
    ├── RestaurantFilters.java             # extended (criteria match, business status)
    ├── DeckSelector.java                  # extended (place id key)
    ├── SearchAnswer.java, SearchAnswerParser.java   # extended
    ├── Restaurant.java                    # extended (optional fields)
    └── Overpass*, Osm* classes            # REMOVED
backend/src/main/resources/application.yml # google-places section added, overpass section removed
backend/dev/overpass-fixture.json           # REMOVED; a Google fixture replaces it (data NOT real)
backend/src/test/java/...                   # mirrors the above

frontend/src/app/
├── search/search-result.ts                # optional fields added
├── search/deck-input.ts                   # maps website, photo attribution, place id
├── restaurant/restaurant.ts               # deck restaurant: optional website / photo attribution
└── deck/swipe-card/                       # photo error → tablecloth; Google attribution (D15)

.env.example, .github/workflows/deploy.yml  # GOOGLE_PLACES_API_KEY name / secret (no value)
```

**Structure Decision**: web application, existing `backend/` and `frontend/` folders. New code stays in the
`hr.emocio.recoresto.search` package, one sub-package per responsibility as in 002 (`api`, `config`, `criteria`,
`llm`, `restaurant`); the `place` sub-package listed (and removed) in the 002 plan is reused for place location, and
a `google` sub-package groups what depends on Google. Package names are proposed for review.

## Architecture

```text
POST /api/search {sentence, position?}
  SearchService
   1. SentenceInterpreter  -> LlmChatClient.complete (no tools) -> SearchAnswerParser
        unreadable: new attempt (D11), then SERVICE_UNAVAILABLE (FR-007)
        not a request: NOT_UNDERSTOOD   outside France: OUTSIDE_FRANCE (no Google request)
   2. Search points: positions from the answer, else PlaceLocator (D13), else device position (D14),
        else PLACE_REQUIRED
   3. Radius: unchanged rule (distance, else walking minutes, else default)
   4. RestaurantSource.find(criteria, points, radius)  = GooglePlacesRestaurantSource
        one Google search per point (and per page, D3) -> GooglePlaceMapper -> candidates
   5. RestaurantFilters: address, business status, radius, open at moment (D9), AND/OR criteria (D7, D8)
   6. DeckSelector: merge by place id, order (D10), deck size, zone
  -> SearchResult (contract: contracts/search-api.openapi.yaml)
GET /api/photos/... (D16) -> GooglePlacesClient photo media -> image (key never reaches the browser)
```

Failure mapping (FR-012, option A): connection error, timeout, HTTP 5xx, HTTP 429 / `RESOURCE_EXHAUSTED`, HTTP
403 / `PERMISSION_DENIED`, HTTP 400 `API_KEY_INVALID`, missing key: all give `SERVICE_UNAVAILABLE` (503). The log
line names the cause (missing key, invalid key, quota, timeout, unavailable) and never the key (contracts/
google-places-requests.md). A missing key is also logged once at startup as a warning; the application still
starts (same convention as the empty `LLM_API_KEY` default).

Technical choices decided here because they follow a convention of the code base (mentioned, not asked):
- `RestClient` with connect/read timeouts, `SearchException(SERVICE_UNAVAILABLE)` on failure (as `OverpassClient`).
- Settings in a `@ConfigurationProperties` record, values in `application.yml`, each overridable by an environment
  variable (`GOOGLE_PLACES_*`), the key without default value (as `llm.api-key`).
- Development fixture file setting (`google-places.fixture-file`), empty by default, data NOT real (as
  `overpass.fixture-file`, 002 O14).
- Request field mask held in one constant (single authoritative list of the Google fields used, FR-008).
- `languageCode=fr` and `regionCode=FR` on every Google request (the product is French and France-only); this
  makes the names of place types French (D23 decides whether they are shown).
- The internal `Restaurant` stays the response record; Google-only data needed for filtering (types, business
  status) lives in an internal record and never reaches the response.
- Logs keep counts, durations and place identifiers only: restaurant names and addresses are no longer logged
  (today `OverpassRestaurantSource.logRestaurants` logs them), because the terms forbid keeping business names and
  addresses (FR-016, research R6).

## Contract changes

Full contract: [contracts/search-api.openapi.yaml](contracts/search-api.openapi.yaml) (002 contract, version bumped
to `0.2.0-draft`). All additions are optional; nothing is removed, renamed or changes meaning (FR-011):

| Addition | Where | Why |
|---|---|---|
| `website` (string, URI) | `Restaurant` | Planned by FR-011 (Google `websiteUri`), consumed by 007. |
| `placeId` (string) | `Restaurant` | Deduplication across places; the only Google identifier allowed with maps (FR-015) and storable without limit (FR-016). |
| `photoAttributions` (array of `{displayName, uri}`) | `Restaurant` | FR-014: author of the photo (Google `authorAttributions`). |
| `photoSourceUri` (string, URI) | `Restaurant` | Link of the photo on Google Maps (policy, research R6); display decided in D15. |
| `attributions` (array of `{provider, providerUri}`) | `Restaurant` | Third-party data providers Google returns with a place (policy, research R6). |
| `GET /api/photos/{placeId}/{photoId}` | new path | Only if D16 = backend photo address; `photoUrl` then holds this relative address. |

`photoUrl` keeps its meaning (an address the browser can load); it now points to a Google photo through the backend
(or to a key-free Google address, D16). Values of `cuisine` and `priceLevel` keep their meaning, but their wording
changes with the source (D5, D23). The error codes are unchanged.

Other contracts touched: the outbound Google requests ([contracts/google-places-requests.md](contracts/google-places-requests.md));
the environment (`GOOGLE_PLACES_API_KEY` in `.env.example` and in the deploy workflow, which today writes only the
`LLM_*` variables to the instance's `.env`).

## Open decisions (product owner / team)

None of these values is set in this plan. Each needs an answer before `/speckit-tasks`. "Rec." is the planner's
recommendation, for information only.

| # | Decision | Options and consequences |
|---|---|---|
| D1 | Time after which Google is "not answering" (FR-012) | Any value in seconds. Shorter: faster "Réessayer", more false failures. Longer: eats into the 20 s of SC-001 (LLM ~1 s). Applies per Google request; with several places/pages, requests run in parallel per place (technical choice) but pages are sequential. |
| D2 | New attempts on transient Google failures (5xx, 429, timeout) | (a) none, "Réessayer" at once; (b) N attempts with waits, like `overpass.retry-delays-seconds`. (b) adds latency and, for 429, spends quota; each failed request is not billed as far as documented (uncertain). |
| D3 | Search endpoint and number of results | (a) Nearby Search: exact circle = radius, types filter, at most 20 places per point, no pagination; deck of 15 may end shorter after filters; zone list ≤ 20 per point. (b) Text Search: free text (cuisine words), circle only as a bias (backend radius filter removes the rest), 20 per page, up to 3 pages = 60 (FR-017); each page is one more billed request and is sequential (latency). Choose the endpoint and the number of pages. |
| D4 | Cache of Google results between searches | (a) none, stateless as today (simplest, each search billed); (b) short in-memory cache keyed by request (place IDs only may be kept without limit, coordinates ≤ 30 days, other content per terms; photo names may not be cached). Rec.: (a) for the POC. |
| D5 | Price level → "€" format of 003 | Google values: `PRICE_LEVEL_FREE`, `INEXPENSIVE`, `MODERATE`, `EXPENSIVE`, `VERY_EXPENSIVE` (+ `UNSPECIFIED` → absent). Give the text for each (e.g. whether VERY_EXPENSIVE is "€€€€" and FREE is shown at all). |
| D6 | Which Google place types count as a restaurant | (a) `restaurant` and the `*_restaurant` types only (this includes `fast_food_restaurant`, `fine_dining_restaurant`...); (b) also some of `bistro`, `diner`, `gastropub`, `bar_and_grill`, `cafe`, `bar`, `pub`, `bakery`, `meal_takeaway`, `food_court`, `sandwich_shop`, `kebab_shop`, `pizza_delivery`... (full list in research R3). List to give. Wider = more cards, more non-restaurants. |
| D7 | How a cuisine of the sentence is matched | (a) the model chooses from a closed list of Google cuisine types (backend drops unknown values); (b) a backend table French word → Google types (team writes it, e.g. "asiatique" → which types); (c) cuisine words sent as Google free text (Text Search only), Google decides. (a)/(b) are testable and deterministic; (c) is not, and AND/OR trees cannot be checked by the backend. For broad cuisines ("asiatique"), which types are included. |
| D8 | Diets | Google indicators found: `servesVegetarianFood` (boolean, Enterprise + Atmosphere tier) and the types `vegetarian_restaurant`, `vegan_restaurant`, `halal_restaurant`; nothing for kosher, gluten-free, etc. Decide: (1) which indicator means which diet (e.g. does `vegan_restaurant` also mean vegetarian); (2) with AND, a restaurant whose diet is unknown is kept or excluded (OR: kept, spec); (3) a diet with no Google indicator at all (halal outside `halal_restaurant`, kosher) is ignored, or makes every place "unknown"; (4) confirm the Atmosphere tier ($40 vs $35 per 1,000, same 1,000 free) that `servesVegetarianFood` brings. |
| D9 | Opening hours and status | (1) Confirm "unknown hours kept" for Google. (2) Use regular weekly hours, or current hours (include exceptional closures, only for the next 7 days). (3) `FUTURE_OPENING` and missing business status: keep or exclude (closed temporarily / permanently are excluded per spec). |
| D10 | Order of the deck (relevance, 002 R5 still open) | (a) Google order (Nearby: popularity; Text: relevance); (b) distance; (c) Google rating (field available in the tier already billed, but ratings are out of scope for display); (d) number of matched criteria then one of the above. Also the order when merging several places. |
| D11 | Recovery of an unreadable model answer (FR-007) | Number of new attempts; same request again (as the empty-answer retry) or a request that shows the model its unreadable answer and asks for the JSON only. Whether this counter is shared with the existing `llm.empty-answer-retries` (renaming it to an "unusable answer" setting changes an existing environment variable). Each attempt ≈ 1 s. |
| D12 | Several JSON objects in the answer (T14) | (a) the first object that reads as an answer; (b) the last one; (c) the answer is unreadable (→ D11). |
| D13 | Locating places (FR-006) | (1) Keep the model's coordinates when valid (no cost, accuracy not checked, 002 O12) and locate only the missing/invalid ones, or always locate with a geocoder. (2) Geocoder: Google Places Text Search (best for landmarks like "Opéra"; Pro tier, $32/1,000, 5,000 free), Google Geocoding ($5/1,000, 10,000 free), or the free French API Adresse (addresses/places in France, was used in 002 before the pivot). (3) Confirm: a place that cannot be located → `PLACE_REQUIRED` (spec assumption). (4) What "invalid position" means (T17): outside lat/lng bounds only, or also outside France. |
| D14 | Device position when the sentence has no place (002 FR-006, 002 O15 still open) | Today the backend ignores the position the frontend sends, so T9 ends `PLACE_REQUIRED`. Use it as the search point when no place is named (rec.), and not otherwise. |
| D15 | Google attribution (FR-014) | Form on the card front: Google Maps logo (16-19 dp, preferred) or the text "Google Maps" (12-16 sp, untranslated); placement (top or bottom of the card content). Photo author: name with link shown on the photo, or only on a larger view (card back, 004); link to the photo on Google Maps. Also the zone list (005) and route (006) screens when they show Google data. To be checked against the current terms by the team. |
| D16 | How photos reach the browser (FR-010, FR-013) | (a) Backend address `GET /api/photos/...` that fetches the Google image on demand: billed only for images actually loaded, adds a backend round trip, anyone reaching the backend can spend photo quota through it; (b) the backend resolves each photo's key-free Google address during the search: every returned restaurant (deck and zone) billed at once ($7/1,000, 1,000 free), adds latency before the first card, lifetime of that address not documented. Also the image width requested (1-4,800 px, same price). |
| D17 | Budget and quotas | Who owns the Google Cloud project and billing; monthly budget alert; daily request caps per SKU in the console. Estimate per search (after free caps): 1 search request ≈ $0.040 per point and per page + photos ≈ $0.007 each (up to 15 for a deck) ≈ $0.15 for the reference sentence. Free caps ≈ 1,000 searches and 1,000 photos per month, so about 66 decks of 15 photos per month for free. |
| D18 | SC-004 | Reference sentences and target shares (photo, hours, price level), required by the spec at this step. |
| D19 | `infra/overpass` (local Overpass instance) | Remove it with the Overpass code, or keep it for another use. The Overpass Java code is removed in any case (FR-001, DRY). |
| D20 | Privacy (002 O5) | The understood place, the search point (possibly the device position) and the cuisine/diet terms go to Google. Team validation required by the spec. |
| D21 | Displayed formats | (1) Cuisine chip: Google's French type label (e.g. "Restaurant italien"), or a short label from a team table, or the raw type; today the chip shows English OSM values. (2) Address: full Google address (ends with ", France") or the short one. (3) Phone: national format (rec. for a France-only product) or international. |

## Risks

- Google search latency is not measured; SC-001 relies on it (measure first, T46).
- The free caps are small for photos (1,000/month); D16 and D17 decide the cost exposure. An open photo endpoint can
  be abused.
- `servesVegetarianFood` is the only diet boolean; vegan/halal rely on place types; diet results may be poorer than
  users expect (D8).
- Nearby Search caps at 20 places per point; after filters the deck may be shorter than 15 (D3).
- Terms: attribution, EEA "with any map" rule and caching come from public pages read on 2026-11-25; the binding
  text is the team's Google Maps Platform agreement (to be confirmed by the team, spec FR-014-FR-016).
- Regular opening hours ignore exceptional closures (D9).
- Changing `RestaurantSource` and removing tool calling conflicts with issue A (see Merge note).
- `.env.example` could not be read by the planner (local permission rule); the task must add the variable name
  there by hand.

## Merge note (issue A)

Issue A (`fix/search-latency`, commit `1e18c7e`, not merged in this worktree) adds `Elapsed`, timing logs in
`LlmChatClient`, `SearchService` and `OverpassClient`, `LlmChatClient.ToolRefused`, and changes the Overpass classes.
008 removes the Overpass classes and the tool calling, and rewrites `SearchService`. Recommended order: merge issue
A into `main` first, rebase `feat/google-places-source`, then implement 008, keeping `Elapsed` and the LLM/search
timing logs (needed for SC-001) and removing `ToolRefused` with the tool loop. If issue A is not merged, 008 must not
re-create a parallel timing helper; the task list then adds `Elapsed` once.

## Blocking `/speckit-tasks`

1. Teammate approval of this plan (constitution I).
2. Answers to D1-D21 (constitution III); D18 is required by the spec at this step.
3. Spec test plan: Expected column empty; blocks `/speckit-implement` (and the tests written first).
4. Issue A merge order decided (Merge note).
5. A Google Cloud project with billing and a key restricted to the Places API (New) must exist for T46/T47 and the
   quickstart (outside this feature's scope, spec "Out of scope").

## Complexity Tracking

No constitution violation to justify.

## Product owner decisions (2026-11-25)

- **Issue A is not merged** before this feature: branch from `main` as it is; `Elapsed` and the timing logs of issue A are not available here (SC-001 is measured from the client side, e.g. request duration), `ToolRefused` does not exist on this branch.
- **Billing (D8 tier, D17)**: Google must not bill anything; the usage must stay within the free monthly caps of every SKU used (requests and photos). The plan must choose the field mask and the photo strategy accordingly, and rely on daily quota caps set in the Google Cloud console so that requests beyond the free caps are refused by Google (then FR-012: "service unavailable"). Budget alerts alone do not stop billing.
- **Privacy (D20)**: sending the place, the search point and the cuisine or diet terms to Google is accepted.
- **Accepted proposals ("ok tout", 2026-11-25)**:
  - D1 Google timeout: 10 s. D2 retry: 1 retry after 1 s on a Google server error, 429 or timeout.
  - D3 Nearby Search (exact radius, at most 20 places per search point, no pagination).
  - D4 no cache between searches.
  - D5 price levels: INEXPENSIVE → €, MODERATE → €€, EXPENSIVE → €€€, VERY_EXPENSIVE → €€€€, FREE → no price.
  - D6 restaurant types: `restaurant` and `*_restaurant` only.
  - D7 cuisines: the language model picks from a closed list of Google types; a broad cuisine ("asiatique") includes every matching type.
  - D8 diets: vegetarian → `servesVegetarianFood` or `vegetarian_restaurant`; vegan → `vegan_restaurant`; halal → `halal_restaurant`; with AND an unknown diet excludes the restaurant; diets Google never states match no place; the Enterprise + Atmosphere tier is kept as long as usage stays within the free caps.
  - D9 hours: restaurants with unknown hours are kept; regular weekly hours; `FUTURE_OPENING` and closed statuses excluded; no status kept.
  - D10 deck order: current `DeckSelector` rule, unchanged.
  - D11 unreadable model answer: reuse `llm.empty-answer-retries` (2), asking the model to fix its answer.
  - D12 several JSON objects: the first complete object.
  - D13 places: the model's coordinates are kept when valid, otherwise located with Google Places Text Search (same key); a place that cannot be located → "place required"; a point outside France → "outside France".
  - D14 no place in the sentence: the device position is the search point.
  - D15 attribution: discreet "Google Maps" text on the photo, photo author in small text under it.
  - D16 photos: option (a), a backend endpoint fetching each image on demand; width 800 px.
  - D19 `infra/overpass` is removed.
  - D21 cuisine chip: Google French label; short address without ", France"; national phone format.
- D18 / SC-004: reference sentence "Je veux manger à opera"; at least 80 % of the deck cards have a photo, opening hours and a price level all at once on the same card (product owner, 2026-11-25). Amended 2026-11-26: photo removed (Google returns none for this project), target is opening hours and price level; the photo is measured by spec 007.
- **Plan approved** by the product owner (2026-11-25; self-approved for the POC, exception to the teammate approval of `specs/README.md`).

### Implementation decisions (product owner, 2026-11-25, during /speckit-implement)
- **Q1 includedTypes limit (50)**: with no cuisine, or when an OR branch holds a diet leaf, send `includedTypes=["restaurant"]` only and filter in the backend with the D6 rule (`restaurant` and `*_restaurant`) — one billed request, equivalent of `amenity=restaurant`. With cuisines, send their types; if more than 50, split into requests of at most 50 types (rare).
- **Q2 lists without a combination tree**: OR between the cuisines, AND with the diets — e.g. (italian OR pizza) AND vegetarian.
- **Q3 cuisine value outside the closed list**: ask the model to fix it with the D11 repair mechanism (reuse `llm.empty-answer-retries`, 2); if still invalid, the outcome is "no result" (002). Never silently widen to "any restaurant".
