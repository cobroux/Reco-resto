# Research: LLM Tool Registry and Reviews Tool

No `NEEDS CLARIFICATION` remains in the spec. The items below are design decisions; each fact about Google that was not checked is marked.

## R1 - Where the tool abstraction lives

- **Decision**: new package `search/tool/` with `LlmTool`, `ToolContext`, `ToolRegistry` and the two tools.
- **Rationale**: the tools depend on `google`, `restaurant` and `config`, so they cannot sit in `llm/` (which only holds the model client and JSON helper). The tests mirror the package.
- **Alternatives**: keeping the tools inside `SearchService` with a `switch` on the name (rejected: it is the duplication the feature removes); a plugin or annotation scan (rejected: two tools do not justify it).

## R2 - How to get the reviews

- **Decision**: a separate Google Place Details request per tool call, field mask `reviews`, `languageCode` from `GooglePlacesProperties`.
- **Rationale**: putting `places.reviews` in `SEARCH_FIELD_MASK` would fetch and bill reviews for every search, even those where the model never asks. A Place Details call costs only when used.
- **Not verified**: the exact Google billing category of the `reviews` field, and whether Google caps the number of reviews it returns per place. Google is believed to return at most 5; the code still enforces `max-reviews`. To check in the Google documentation before the implementation.
- **Alternatives**: add `places.reviews` to the search mask (rejected: cost on every search); scrape Google Maps (rejected: not allowed by the Google terms).

## R3 - Rating on the API

- **Decision**: add `rating` and `ratingCount` to `Restaurant`, filled by `GooglePlaceMapper` from `rating` and `userRatingCount`.
- **Rationale**: both fields are already requested in `SEARCH_FIELD_MASK`, so there is no extra Google cost. It also gives the model-facing candidate list and the API the same single representation (the raw JSON read in `SearchService.forModel` is removed).
- **Alternatives**: expose the raw Google node (rejected: leaks the provider shape); a separate rating endpoint (rejected: more calls for data already in hand).

## R4 - Sharing the last search between tools

- **Decision**: `ToolContext` carries the position and the last non-empty `ToolRun`; `GetPlaceReviewsTool` resolves `rN` through it.
- **Rationale**: the ids `r1..rN` only exist for the last search. The state lives for one request, so there is no cross-request or thread-safety concern.
- **Alternatives**: put the place id in the tool arguments (rejected: the model could then ask for any place, and "no restaurant can be invented" is a rule of the current design).

## R5 - What is sent to the model from a review

- **Decision**: the star rating and the text only; no author name, no date.
- **Rationale**: review text is model-only (spec FR-006), so Google's display attribution is not triggered, and less text means fewer tokens.
- **Not verified**: Google's terms on feeding review text to a language model (open item 4 of the plan).

## R6 - Contract change

- **Decision**: a delta contract in `specs/009-llm-tool-registry/contracts/search-api-rating.openapi.yaml`; the 008 contract is left untouched.
- **Rationale**: the constitution forbids editing an approved spec during implementation, and 008 is another feature's folder.
