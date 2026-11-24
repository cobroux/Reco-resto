# Data Model: Swipe Deck

Frontend view models only. No persistence.

## Restaurant (view model)

Subset of feature 002's Restaurant (002 FR-012) used by the deck. Optional fields may be missing (002 assumption).

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | string | yes | Unique within a search; used for no-repeat rules |
| `name` | string | yes | |
| `distanceMeters` | number | yes | From the search place; formatted by `distance.ts` |
| `cuisine` | string | yes | Display label, e.g. "Italienne" |
| `keywords` | string[] | yes (may be empty) | e.g. "Options végé", "Épicé" |
| `priceLevel` | string | no | e.g. "€€"; chip hidden when missing |
| `weeklyHours` | OpeningRange[] per weekday | yes | Used for the targeted day |
| `photoUrl` | string | no | Missing → tablecloth visual (US1 AS3) |
| `dish` | `{ name: string; photoUrl?: string }` | no | Missing → default dish visual, no name (US1 AS4) |

`OpeningRange`: `{ open: "HH:mm", close: "HH:mm" }`.

## DeckInput (from `DeckSource`)

| Field | Type | Notes |
|-------|------|-------|
| `momentLabel` | string \| null | Raw expression from 002 ("ce soir"); null → "maintenant" |
| `targetDateTime` | ISO string | Computed by 002 (D8, to confirm in 002's plan); used for the opening status |
| `restaurants` | Restaurant[] | Ranked, most relevant first; already filtered (open, with address) by 002 |

## Reaction

| Field | Type | Notes |
|-------|------|-------|
| `restaurantId` | string | |
| `kind` | `'liked' \| 'disliked' \| 'picked'` | `picked` becomes `liked` on return from the route screen (FR-014) |

## DeckState

| Field | Type | Notes |
|-------|------|-------|
| `size` | number | N = min(DECK_SIZE, restaurants.length), fixed for the deck (FR-002, US3 AS2) |
| `shown` | `{ restaurant, reaction? }[]` | Cards already displayed, in order; the last one without reaction is the current card |
| `planned` | Restaurant[] | Cards to come; length = size − shown.length |
| `candidates` | Restaurant[] | All restaurants of the input (pool for refinement) |
| `refinementDegraded` | boolean | True after a failed refinement until the next success (FR-013) |
| `status` | `'browsing' \| 'picked' \| 'finished'` | |

Derived: `position` (1-based) = index of the current card + 1; progress = "position sur size"; `canRewind` = position > 1.

### Rules

- **R1 No repeat**: a restaurant with an active reaction is never in `planned` (FR-006, SC-003). Restaurants whose reaction was cancelled by rewind become eligible again (D7).
- **R2 Fixed size**: `shown.length + planned.length = size` at all times.
- **R3 Refinement result**: filtered by R1, truncated to the slots left; if shorter, completed with the previous planned cards not yet used (keeps R2).

## State transitions

```text
browsing --react(liked|disliked), not last--> browsing (next card; refine)
browsing --react(liked|disliked), last-------> finished (navigate /results)
browsing --react(picked)---------------------> picked   (navigate /route/:id)
picked   --returnFromPick--------------------> browsing (picked→liked, next card)  | finished, navigate /results, if it was the last card (D12)
browsing --rewind, position > 1--------------> browsing (previous card current, its reaction cancelled; refine)
browsing --rewind, position = 1--------------> browsing (no-op; "<" disabled)
any      --refine error----------------------> same status, refinementDegraded = true, planned unchanged
any      --refine success--------------------> same status, refinementDegraded = false, planned replaced (R1–R3)
```

## SessionReactions (shared with 005, 006)

| Field | Type | Notes |
|-------|------|-------|
| `restaurants` | Restaurant[] | The deck input restaurants |
| `reactions` | Reaction[] | Active reactions only (cancelled ones removed) |

Lives in memory for the browser session; cleared on refresh (spec edge case).
