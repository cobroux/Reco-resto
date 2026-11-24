# Data Model: Natural-Language Restaurant Search

Derived from the spec (Key Entities, FR-001 to FR-016). Field names are proposals for plan review.

## SearchRequest

| Field | Type | Rule |
|-------|------|------|
| sentence | string | Required, non-blank. |
| position | {latitude, longitude} | Optional; only used when the sentence has no place (FR-006). |
| retry | - | Not a field: "Réessayer" re-sends the same request. |

## SearchCriteria

| Field | Type | Rule |
|-------|------|------|
| cuisines | list of string | Cuisine types understood (e.g. `asian`). |
| diets | list of string | Diets understood (e.g. `vegetarian`). |
| combination | tree of AND/OR over cuisines and diets | Keeps the grouping expressed by the user (FR-002, edge case "asiatique ou italien, et végétarien"). |
| places | list of string | Zero, one or several place names (FR-015). Empty means "use the position". |
| moment | date-time or `now` | Defaults to `now` (FR-007); MUST NOT be in the past. |
| distanceLimit | distance, optional | Replaces the default radius when present (FR-005). |

Budget and specific dish are never stored (FR-003): they are dropped by the extractor.

The allowed values of `cuisines` and `diets` (closed vocabulary or free text) are NOT defined by the
spec; NEEDS CLARIFICATION. It depends on the restaurant data source (O1).

## Restaurant (FR-012; each field when available)

name, location (latitude, longitude), distance from the search place, cuisine type, diets satisfied,
keywords, price level, weekly opening hours, photo, signature dish, address, phone number, menu
(dishes with description and price).

Validation: a restaurant with no address is excluded from the deck (FR-016); a restaurant closed at the
targeted moment is excluded (FR-007).

## SearchResult

| Field | Type | Rule |
|-------|------|------|
| criteria | SearchCriteria | The understood criteria (FR-014). |
| deck | list of Restaurant | At most `deckSize` (default 15), most relevant first, no duplicates (FR-008, FR-009, FR-015). |
| zone | list of Restaurant | All restaurants of the search zone; returned only when requested (FR-013). |

## Configuration (not user settings)

| Key | Default | Source |
|-----|---------|--------|
| search.default-radius-km | 2.5 | FR-004 |
| search.deck-size | 15 | FR-008 |

## Error outcomes (User Story 3)

`NOT_UNDERSTOOD`, `NO_RESULT`, `OUTSIDE_FRANCE`, `SERVICE_UNAVAILABLE`, plus `PLACE_REQUIRED` when the
sentence has no place and no position was given (FR-006).
