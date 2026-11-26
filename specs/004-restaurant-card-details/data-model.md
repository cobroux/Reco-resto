# Data Model: Restaurant Card Details

All types are frontend view models (TypeScript, `readonly`).

## MenuItem (new, in `restaurant/restaurant.ts`)

| Field | Type | Notes |
|-------|------|-------|
| `name` | `string` | Dish name, shown as given |
| `description` | `string?` | Shown when present |
| `price` | `string?` | Shown as given, no parsing (the backend sends a string) |

Mirrors `MenuItem.java`. A second declaration exists in `search/search-result.ts` for the API shape (same pattern as `OpeningPeriod` vs `OpeningRange`).

## Restaurant (extended, `restaurant/restaurant.ts`)

Adds: `menu?: readonly MenuItem[]`, `phone?: string`. Existing fields (`weeklyHours`, `address`, `keywords`, `priceLevel`, `distanceMeters`, `cuisine`) are used as is.

Rules (FR-003): the "Menu" section is displayed only when `menu` is defined and not empty; the "Horaires" section only when at least one day of `weeklyHours` has a range.

## Weekly hours row (derived, `format/weekly-hours.ts`)

| Field | Type | Notes |
|-------|------|-------|
| `day` | `string` | French day name; Monday first |
| `ranges` | `string` | "12:00 – 14:30 · 19:00 – 22:30", or "Fermé" |
| `targeted` | `boolean` | True for `targetWeekday(targetDateTime)` (FR-004) |

## RoutePreview (new, `deck/route-preview.service.ts`)

| Field | Type | Notes |
|-------|------|-------|
| `modes` | `readonly { mode: 'walk' \| 'bike' \| 'car' \| 'transit'; minutes: number }[]` | Only modes with a possible route (spec edge case); empty array is allowed |

## State (not persisted)

| State | Owner | Values |
|-------|-------|--------|
| `flipped` | `SwipeCardComponent` | `false` (front) / `true` (back) |
| `previewOpen` | `SwipeCardComponent` | `false` / `true`; reset when flipping back |
| `preview` | `RoutePreviewComponent` | loading / loaded / failed |

Viewing the preview changes none of the deck state (FR-006).
