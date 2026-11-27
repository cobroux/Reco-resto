# Data Model: LLM Tool Registry and Reviews Tool

No persistence; every item below lives for one request.

## Restaurant (extended)

Existing record `hr.emocio.recoresto.search.restaurant.Restaurant`, two optional fields added at the end:

| Field | Type | Rule |
|---|---|---|
| `rating` | `Double` | Google user rating; absent when Google gives none (FR-007) |
| `ratingCount` | `Integer` | Number of ratings; absent when Google gives none |

- Both are omitted from the JSON when null (the record is already `NON_NULL`).
- `rating` and `ratingCount` are filled by `GooglePlaceMapper.map` from the Google fields `rating` and `userRatingCount` only when they are numbers.
- `withLocation` and `withReason` keep both values.

## GoogleReview (new, internal)

Record in `search/google/`, never serialized to the API.

| Field | Type | Rule |
|---|---|---|
| `rating` | `Integer` | Stars given by the reviewer; absent when not a number |
| `text` | `String` | Review text in the configured language; reviews without text are skipped; cut to `max-review-length` (value to decide) |

## ToolRun (moved)

Moved unchanged from `SearchService` to `SearchRestaurantsTool` (package-visible to `ToolContext`): `criteria`, `candidates` (backend order), `zone`, `rawById`.

## ToolContext (new)

| Field | Rule |
|---|---|
| `position` | The user position of the request, never null |
| `lastRun` | The last `ToolRun` that has candidates, or the first run when none had any (rule kept from today) |

## Tool results (text sent to the model)

See [contracts/llm-tools.md](contracts/llm-tools.md).

## Settings (`search.tool.*`, record `ToolSearchProperties`)

| Key | Meaning | Value |
|---|---|---|
| `max-reviews` | Reviews kept per restaurant | 5 (decided in the spec) |
| `max-review-calls` | Reviews calls the model may make per sentence | **to decide** (plan open item 1) |
| `max-review-length` | Characters of one review sent to the model | **to decide** (plan open item 2) |

`max-rounds` stays 3 until plan open item 3 is answered.
