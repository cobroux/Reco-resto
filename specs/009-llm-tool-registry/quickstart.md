# Quickstart: validate the feature

Prerequisites: `backend/.env` with `GOOGLE_PLACES_API_KEY` and `LLM_API_KEY` (see `.env.example`). Never commit keys.

## 1. Tests

```text
mvn -f backend test
```

Expected: all tests pass, and the `SearchServiceTest` cases that existed before the change keep their assertions (SC-001).

## 2. Run the backend

```text
mvn -f backend spring-boot:run
```

## 3. Rating in the answer (US1 scenario 4, SC-003)

Send a search (see the search request of [specs/008-google-places-source/contracts/search-api.openapi.yaml](../008-google-places-source/contracts/search-api.openapi.yaml)) with a French sentence and a position in France. Expected: each restaurant that Google rates has `rating` and `ratingCount`; a restaurant without rating has neither; no answer contains review text.

## 4. Reviews tool (US1 scenarios 1 to 3)

Search with a sentence about quality, for example "le meilleur ramen près de moi". Expected in the backend logs: a `get_place_reviews` tool call after the search, and a pick reason that reflects the rating. Then run with an invalid key or no network: the search still returns a deck, without reviews (SC-002).

## 5. Unchanged behavior (US2, FR-008)

Search with a sentence that is not about quality: the tool list, the picks and the card photos behave as before.
