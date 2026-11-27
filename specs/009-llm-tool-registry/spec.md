# Feature Specification: LLM Tool Registry and Reviews Tool

**Feature Branch**: `feat/llm-tool-registry`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "LLM tool registry for the search: (1) refactor SearchService's hardcoded search_restaurants tool into a registry of LLM tools; (2) add an LLM photo orchestrator that chooses between, or merges, Google Places photos and Unsplash photos for restaurant cards (photos only; restaurant search stays on Google Places), with a deterministic fallback when the LLM fails; (3) add a live LLM tool that fetches Google Places user ratings and reviews for a restaurant at search time (no storage, no database)."

## Clarifications

### Session 2026-11-27

- Q: When the orchestrator "merges" Google and Unsplash photos, what should a card show? → A: One photo per card, Google first and Unsplash second.
- Q: The photo order is now fixed, so what should the language model do about photos? → A: Nothing. No LLM is used for photos; the LLM photo orchestrator is dropped from this feature.
- Q: Where does the restaurant website photo (spec 007) fit in the photo order? → A: Website first. This answer rested on a wrong description of today's behavior and was superseded in the planning session below.
- Q: May the restaurant data and the search API contract get new rating fields? → A: Yes. Add the Google rating and the number of ratings to the restaurant data and the API. Review text stays model-only and is not exposed by the API.
- Q: How many Google reviews does the tool give the model per restaurant, and in which language? → A: Up to 5 reviews, in the configured search language (French today). The number is a setting.

### Session 2026-11-27 (planning)

- Correction: the frontend already shows the data-source (Google) photo before the website photo, and the website enrichment ends with Unsplash. Today's real order is Google, then website, then Unsplash.
- Q: Which photo order do you want? → A: Keep today's order (Google, website, Unsplash). There is no photo change in this feature.
- Q: Is displaying the Google rating on the card part of this feature? → A: No. Backend and API only; the card display is a follow-up.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ratings and reviews on demand (Priority: P1)

When a search needs to judge restaurant quality (for example "best rated pizza nearby"), the language model can ask for a restaurant's Google user rating and reviews and use them to pick and explain its choices. The rating and the number of ratings also come back in the search answer.

**Why this priority**: It is the only capability that is new in this feature.

**Independent Test**: Search for "best rated sushi nearby" with a fixture place that has ratings. Check that the model calls the reviews tool, that the pick's reason reflects the rating, and that the answer carries the rating and the number of ratings.

**Acceptance Scenarios**:

1. **Given** a candidate restaurant with Google ratings, **When** the model requests its reviews, **Then** the tool returns the rating, the number of ratings and at most 5 reviews.
2. **Given** a candidate restaurant with no rating, **When** the model requests its reviews, **Then** the tool returns an explicit "no rating" result and the search continues.
3. **Given** Google Places is unavailable or its quota is exhausted, **When** the model requests reviews, **Then** the search still returns results without reviews.
4. **Given** a restaurant with a rating, **When** a search answer is returned, **Then** its data carries the rating and the number of ratings, and never the review text.

---

### User Story 2 - Add a tool without rewriting the search (Priority: P2)

A developer adds a new language-model tool by registering one self-contained unit, without editing the main search flow.

**Why this priority**: It is the enabler for story 1 and for later tools, but it brings no direct user-visible change.

**Independent Test**: Existing search behavior is unchanged after the refactor: the current search tests pass with no change to their expected results.

**Acceptance Scenarios**:

1. **Given** the existing restaurant search tool moved into the registry, **When** a user searches in natural language, **Then** the results are identical to those before the change.
2. **Given** a model call to an unknown tool, **When** the search runs, **Then** the model receives an error message and the search continues within its existing round and call limits.

---

### Edge Cases

- A model call to a tool that exists but with invalid arguments (for example a restaurant id not in the current candidates).
- The model exceeds the maximum number of rounds or tool calls.
- Google returns fewer than 5 reviews, or none, for a restaurant that has a rating.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The search MUST expose its language-model tools through a registry, so that each tool is defined, described and executed in one place.
- **FR-002**: The existing restaurant search tool MUST move into the registry with no change to its observable behavior, limits (rounds, calls, radius, query length) or results.
- **FR-003**: The system MUST provide a tool that returns, for a restaurant in the current candidates, its Google user rating, the number of ratings and its reviews, fetched live at search time.
- **FR-004**: The reviews tool MUST NOT store any data; results exist only for the duration of the request.
- **FR-005**: When a rating cannot be fetched or does not exist, the search MUST still return results, and the model MUST be told that no rating is available.
- **FR-006**: The reviews tool MUST return at most 5 reviews per restaurant (a setting), in the configured search language. Review text is only given to the model and never exposed by the API.
- **FR-007**: The restaurant data and the search API contract MUST carry the Google rating and the number of ratings, both optional (absent when Google has none). Review text MUST NOT be exposed by the API.
- **FR-008**: The card photo order (Google photo, then website photo, then Unsplash photo) and the card display MUST NOT change.

### Key Entities

- **LLM tool**: A capability the language model can call during a search, with a name, a description, accepted arguments and a result.
- **Place rating**: A restaurant's Google user rating and number of ratings (carried by the restaurant data and the API), plus at most 5 reviews (model-only).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All existing search tests pass unchanged after the refactor (zero behavior regressions).
- **SC-002**: A search that uses the reviews tool still completes when ratings are missing or the source fails, in 100% of tested failure cases.
- **SC-003**: 100% of restaurants that Google rates carry the rating and the number of ratings in the search answer, and 0% of answers contain review text.

## Assumptions

Each item below is unverified and must be validated before the implementation:

- The "reward restaurants API" in the request means Google Places ratings and reviews, which is the same provider as the search.
- No database or cache is added; data is fetched live.
- Existing round and call limits for search tools (`search.tool.*`) also apply to the new tool.
- [NEEDS VALIDATION: Google's terms on sending review text to a language model have not been verified.]
- Out of scope: the photo orchestrator (dropped), any photo change, and displaying the rating on the card (follow-up).
