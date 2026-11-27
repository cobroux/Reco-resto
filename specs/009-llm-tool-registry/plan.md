# Implementation Plan: LLM Tool Registry and Reviews Tool

**Branch**: `feat/llm-tool-registry` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-llm-tool-registry/spec.md`

## Summary

Two backend changes, no frontend change:

1. **Registry**: the single hardcoded `search_restaurants` tool and its dispatch leave `SearchService` and become one `LlmTool` among others, found through a `ToolRegistry`. Observable search behavior stays identical (SC-001).
2. **Reviews tool**: a new `get_place_reviews` tool gives the model the rating, the number of ratings and at most 5 Google reviews of a candidate, fetched live with a Google Place Details request (no storage). The rating and the number of ratings also become two optional fields of `Restaurant` and of the search API; review text never leaves the backend.

The LLM photo orchestrator of the original request is dropped, and photos do not change (spec Clarifications).

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1, Maven (`backend/`, package `hr.emocio.recoresto`)

**Primary Dependencies**: existing only: Spring `RestClient` (Google client), Jackson 3 (`tools.jackson`), JUnit 5, Mockito. No new dependency.

**Storage**: N/A (live calls only, FR-004)

**Testing**: JUnit 5 + Mockito, `MockRestServiceServer` for the Google client (same as `GooglePlacesClientTest`); tests mirror the main packages under `backend/src/test/java`

**Target Platform**: the existing Spring Boot service

**Project Type**: web service (backend only for this feature)

**Performance Goals**: no new target; a reviews call adds one Google request and one model round only when the model asks for it (see Risks)

**Constraints**: existing limits `search.tool.*` stay; Google key only in the `X-Goog-Api-Key` header; no review text in any API answer

**Scale/Scope**: 2 tools, 1 new Google operation, 2 new optional API fields

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1.*

| Principle | Status |
|---|---|
| I. Spec-Driven Development | The spec is `Draft`. A teammate other than the author MUST approve spec and plan before `/speckit-tasks`. The branch `feat/llm-tool-registry` does not exist yet (creation was denied earlier in the session). |
| II. Test-First | Tests are listed per task group in Test Plan; they are written and seen failing before the code. |
| III. Never Guess | Six values or facts are not decided; they are listed in "Open items" and no value is assumed in this plan. |
| IV. DRY | See "Reuse, extension and replacement" below. |
| V. English, Conventional Commits | Everything is in English. Commits: `refactor:` for the registry, `feat:` for the reviews tool. |

No violation, so Complexity Tracking is empty.

## Reuse, extension and replacement

Found by searching the code base by behavior (tool dispatch, rating, Google request, JSON text helper):

- **Reused as is**: `GooglePlacesClient.call(...)` (key check, retries of D2, failure logs), `API_KEY_HEADER`/`FIELD_MASK_HEADER`, `GooglePlacesProperties.languageCode()`; `GooglePlaceMapper.IDENTIFIER` (validates the place id before it enters a URL); `JsonAnswers.firstObject`; `LlmChatClient`; `RestaurantFilters`, `DeckSelector`, `MomentResolver`; `GooglePlaceMapper.PRICE_LEVELS` and `GooglePlaceTypes.DIET_TYPES` (still the single tables).
- **Extended in place**: `GooglePlacesClient` (new `placeReviews`), `Restaurant` (two fields), `GooglePlaceMapper.map` (fills them), `ToolSearchProperties` (review settings), `application.yml`.
- **Replaced (removed in the same change)**:
  - `SearchService.TOOL_NAME`, `TOOLS`, `runTool`, `forModel`, `ToolRun`, `PRICE_LEVELS`, `DIETS`, `sorted`, `putIfPresent`: moved into `SearchRestaurantsTool`.
  - The inline name check `TOOL_NAME.equals(...)` (`SearchService.java:174-175`): replaced by a registry lookup.
  - The raw JSON read of rating and count in `forModel` (`SearchService.java:289-291`): replaced by the new `Restaurant` fields, so the rating has one representation.
  - The private `text(JsonNode)` helpers, identical in `SearchService` and `GooglePlaceMapper` (and any other identical copy the implementation finds): replaced by one `JsonAnswers.text`.

## Design

### Tool registry (`search/tool/`)

- `LlmTool`: `name()`, `definition()` (the OpenAI function map sent to the model), `callLimit()`, `execute(String argumentsJson, ToolContext context)` returning the text sent back as the `role:"tool"` message.
- `ToolContext`: the user `Position` and the state shared between the tools of one search, namely the last non-empty `ToolRun` (criteria, candidates in backend order, zone, raw Google nodes by place id). It is created by `SearchService` for each request; there is no shared mutable state between requests.
- `ToolRegistry`: a Spring bean built from all `LlmTool` beans; refuses two tools with the same name at startup; offers `definitions()` and `find(name)`.
- `SearchRestaurantsTool`: the current `runTool` and `forModel` moved without logic change. It keeps the rule "a new search replaces the previous one only when it found something", now by writing into `ToolContext`.
- `SearchService.search` keeps its loop, limits, fallbacks and error codes. It calls `registry.definitions()` instead of `TOOLS`, dispatches with `registry.find`, counts calls per tool against `callLimit()`, and reads the last run from `ToolContext`. An unknown tool and an over-limit call still get the same error message as today.
- The system prompt gains the reviews tool step. The wording is a behavior change of the model, so it is written in the tasks with its tests and reviewed by the team.

### Reviews tool

- `GetPlaceReviewsTool` (`get_place_reviews`, argument `id`, required, `r1..rN`): resolves the id to a candidate of the context's last run, takes its `placeId`, calls `GooglePlacesClient.placeReviews`, and answers `{"id","rating","ratingCount","reviews":[{"rating","text"}]}`.
- An id that is not in the current candidates, or a candidate without `placeId`, answers an error object, never an exception (edge case of the spec).
- A candidate without rating and without reviews answers `{"id","message":"no rating available"}` (FR-005). Any `SearchException` from Google is caught: the answer is `{"id","error":"reviews unavailable"}` and the search continues (US1 scenario 3, SC-002).
- Review author names are not sent to the model: the text is model-only and not displayed, so no attribution is needed.
- `GooglePlacesClient.placeReviews(placeId)`: `GET {base-url}/places/{placeId}` with the headers of the existing client, field mask `reviews`, `languageCode` from `GooglePlacesProperties`. It validates the id with `GooglePlaceMapper.IDENTIFIER`, uses `call("reviews", ...)` for retries and logs, takes the first `max-reviews` reviews, and returns an empty list on a 404. Request detail: [contracts/llm-tools.md](contracts/llm-tools.md).
- Reviews are fetched with a separate Place Details request, not added to the search field mask, so that the extra Google cost applies only when the model asks (research R2).

### Rating on the API

- `Restaurant` gets `Double rating` and `Integer ratingCount`, both optional and omitted when null (`@JsonInclude(NON_NULL)` already applies). `GooglePlaceMapper.map` fills them from `rating` and `userRatingCount`, which are already in `SEARCH_FIELD_MASK`. No new Google field in the search.
- Contract delta: [contracts/search-api-rating.openapi.yaml](contracts/search-api-rating.openapi.yaml). Spec 008's contract is not edited (constitution I).
- Frontend: the TypeScript `Restaurant` interface ignores unknown fields, so no frontend change is needed; displaying the rating is a follow-up (spec Assumptions).

## Project Structure

### Documentation (this feature)

```text
specs/009-llm-tool-registry/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── llm-tools.md
│   └── search-api-rating.openapi.yaml
├── checklists/requirements.md
└── tasks.md              # created by /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/hr/emocio/recoresto/search/
├── SearchService.java                 # loop kept; tool code removed
├── tool/                              # new
│   ├── LlmTool.java
│   ├── ToolContext.java
│   ├── ToolRegistry.java
│   ├── SearchRestaurantsTool.java     # moved from SearchService
│   └── GetPlaceReviewsTool.java
├── google/GooglePlacesClient.java     # + placeReviews
├── google/GooglePlaceMapper.java      # + rating fields; uses JsonAnswers.text
├── google/GoogleReview.java           # new record (rating, text)
├── llm/JsonAnswers.java               # + text(JsonNode)
├── restaurant/Restaurant.java         # + rating, ratingCount
└── config/ToolSearchProperties.java   # + review settings
backend/src/main/resources/application.yml   # + review settings
backend/src/test/java/hr/emocio/recoresto/search/
├── SearchServiceTest.java             # adapted: same cases, same expected results
├── tool/{ToolRegistryTest, SearchRestaurantsToolTest, GetPlaceReviewsToolTest}.java
├── google/{GooglePlacesClientTest, GooglePlaceMapperTest}.java   # extended
└── api/SearchControllerTest.java      # answer carries rating, never review text
```

**Structure Decision**: backend only, in the existing `search` package tree; one new sub-package `tool/` mirrored in the tests.

## Test Plan (tests first)

- `ToolRegistryTest`: finds a tool by name; unknown name is empty; duplicate names fail at startup; definitions list every tool.
- `SearchRestaurantsToolTest`: the `runTool` and `forModel` cases currently exercised through `SearchServiceTest` (clamping, diets, prices, moment fallback, candidate ids, empty result keeps the previous run).
- `SearchServiceTest`: existing cases keep their assertions unchanged (SC-001); added cases: unknown tool, over-limit call, a search followed by a reviews call.
- `GetPlaceReviewsToolTest`: known id gives rating and reviews; unknown id; no placeId; no rating (FR-005); Google failure is caught (SC-002); author names absent.
- `GooglePlacesClientTest`: request path, headers and field mask, `languageCode`, at most `max-reviews` reviews, 404 gives empty, 429 and 5xx follow D2, invalid id sends no request, missing key sends no request.
- `GooglePlaceMapperTest`: rating and count filled when numeric, absent otherwise.
- `SearchControllerTest`: the JSON of a rated restaurant has `rating` and `ratingCount`; a serialized answer never contains review text (SC-003).

## Open items (not decided; no value assumed)

1. **Review calls per search**: the existing `max-tool-calls: 2` counts searches. How many `get_place_reviews` calls may the model make for one sentence (new setting `search.tool.max-review-calls`)?
2. **Review text length**: maximum characters of one review sent to the model (new setting, like `max-reason-length: 120`).
3. **Model rounds**: `max-rounds: 3` fits search, reviews and final answer. If the model searches twice, no round is left for the final answer and the search falls back to backend order. Keep 3 or raise it?
4. **Google terms**: is sending review text to a language model allowed (spec Assumptions)?
5. **Google cost**: confirm the billing category of a Place Details request with the `reviews` field (research R2); it was not verified.
6. **Development fixture**: with `google-places.fixture-file` set, the proposal is that `placeReviews` returns an empty list (and logs that the data is not real). Confirm.

## Risks

- Each reviews call is one billed Google request and one more model round (latency); the limits in items 1 and 3 bound both.
- The system prompt change can alter which restaurants the model picks; the team should review the wording and try real sentences (quickstart).
- `Restaurant` has two constructors besides the canonical one; its callers (`GooglePlaceMapper`, `withLocation`, `withReason`, test helpers `TestRestaurants`, `DeckSelectorTest`, `SearchControllerTest`) are all updated in the same change.

## Complexity Tracking

None.
