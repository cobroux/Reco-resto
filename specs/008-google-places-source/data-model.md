# Data Model: Google Places Restaurant Source

Builds on [002 data-model](../002-natural-language-search/data-model.md). Only what changes is described. "D#"
refers to the open decisions of [plan.md](plan.md); values depending on them are not set here.

## Search criteria (`SearchCriteria`, `CriteriaNode`) - unchanged

Fields, defaults and JSON shape unchanged (cuisines, diets, combination, places, moment, distanceLimitMeters,
walkingMinutes). The values of `cuisines` follow D7 (Google cuisine types from a closed list, or French words mapped
by a backend table); `diets` keep the existing identifiers (`vegetarian`, `vegan`, `halal`, ...).

Validation: produced by the model only; never contains data-source query text (FR-002). Unknown cuisine values are
dropped by the backend when D7 = closed list.

## Model answer (`SearchAnswer`) - extended

| Field | Type | Rule |
|---|---|---|
| state | one of `UNDERSTOOD`, `NOT_A_REQUEST`, `OUTSIDE_FRANCE`, `UNREADABLE` | replaces the two booleans `understood` / `outsideFrance` (or keeps them and adds `unreadable`: implementation detail) |
| criteria | `SearchCriteria` | present when `UNDERSTOOD`; missing in the answer → empty criteria (FR-005) |
| places | list of `NamedPlace` | one per place of the answer (from `centers`, completed by `criteria.places` names without center) |
| restaurants | — | **removed**: the model never provides restaurants |

`NamedPlace(name, position)`: `position` is null when the answer gave none or an invalid one (D13 (4)); such
places are located by `PlaceLocator` (FR-006).

State transitions of one sentence:

```text
LLM answer
  ├─ blank / no JSON object / invalid JSON ─► UNREADABLE ─► new attempt (D11) ─► ... ─► SERVICE_UNAVAILABLE (503)
  ├─ several objects ─► choice per D12 (or UNREADABLE)
  ├─ understood=false ─► NOT_A_REQUEST ─► NOT_UNDERSTOOD (422)
  ├─ outsideFrance=true ─► OUTSIDE_FRANCE (422), no Google request
  └─ understood=true ─► UNDERSTOOD
        places with position ─────────────┐
        places without position ─► PlaceLocator ─► located / not locatable ─► PLACE_REQUIRED (422, D13 (3))
        no place ─► device position (D14) or PLACE_REQUIRED (422)
                                           └─► search points
```

## Search point - unchanged concept

A `Position` per located place (or the device position). Radius rule unchanged (`SearchService.radiusMeters`).

## Google place (internal, never in the response)

`GooglePlace`: what the backend keeps from one Google result before filtering.

| Field | From Google | Use |
|---|---|---|
| id | `id` | dedup key, `Restaurant.placeId` |
| types, primaryType | `types`, `primaryType` | restaurant check (D6), cuisine match (D7), diet indicators (D8) |
| businessStatus | `businessStatus` | `CLOSED_TEMPORARILY` / `CLOSED_PERMANENTLY` excluded; others per D9 (3) |
| servesVegetarianFood | `servesVegetarianFood` (true / false / absent) | diet indicator (D8) |
| restaurant | mapped `Restaurant` | the response record |

## Restaurant - extended (response record)

| Field | Source (Google) | Mapping rule |
|---|---|---|
| name | `displayName.text` | required; place without name dropped |
| location | `location` | invalid coordinates → absent (then excluded: radius unknown, existing rule) |
| distanceMeters | computed | distance to the nearest search point (`Position.distanceMetersTo`), rounded |
| cuisine | `primaryTypeDisplayName.text` or a team label or `primaryType` | D21 (1) |
| diets | indicators | listed only when stated (FR-009); table D8 (1) |
| keywords, signatureDish, menu | — | absent (not provided) |
| priceLevel | `priceLevel` | table D5; `UNSPECIFIED`/absent → absent |
| openingHours | `regularOpeningHours.periods` (or current, D9 (2)) | `Map<DAY, List<OpeningPeriod>>`, see below; absent when Google has none |
| photoUrl | first `photos[]` entry | address without key (D16); absent when no photo |
| address | `formattedAddress` or `shortFormattedAddress` | D21 (2); absent → excluded (002 FR-016) |
| phone | `nationalPhoneNumber` or `internationalPhoneNumber` | D21 (3) |
| **website** (new) | `websiteUri` | as given |
| **placeId** (new) | `id` | as given |
| **photoAttributions** (new) | `photos[0].authorAttributions[]` → `{displayName, uri}` | empty list → absent |
| **photoSourceUri** (new) | `photos[0].googleMapsUri` | as given |
| **attributions** (new) | `attributions[]` → `{provider, providerUri}` | empty → absent |

Validation (unchanged rules, `RestaurantFilters`): address present; location and distance known and distance ≤
radius; not known to be closed at the targeted moment (unknown hours kept, D9 (1)); new: business status (D9 (3));
AND/OR criteria tree satisfied (`CriteriaMatcher`, D7/D8; unknown diet under AND per D8 (2)).

### Opening hours mapping (`GoogleOpeningHours`)

- Day: Google `day` 0 = Sunday … 6 = Saturday → `SUNDAY` … `SATURDAY` (key format of `Restaurant.openingHours`).
- Times: `hour`/`minute` → `"HH:mm"`.
- Period within one day → one `OpeningPeriod(opens, closes)` on that day.
- Period over midnight (close day ≠ open day) → `(opens, "00:00")` on the open day and `("00:00", closes)` on the
  close day (existing rule: closing not after opening = until end of day).
- Open without close (24 h) → `("00:00", "00:00")` every day.
- `periods` empty with `regularOpeningHours` present means "never open" for Google, but the existing model cannot
  tell it from "unknown" (`RestaurantFilters` keeps a restaurant whose days are all empty). Such a place is treated
  as unknown unless D9 (1) says otherwise (how often Google returns it is not known).

## Google attribution

- Global: the "Google Maps" mark on every screen showing Google data without a Google map (form D15). Not a
  response field: a constant of the frontend.
- Per restaurant: `attributions` (data providers) and `photoAttributions` / `photoSourceUri` (photo author and
  source), all optional.

## Configuration (`GooglePlacesProperties`, prefix `google-places`)

| Key | Env override | Value |
|---|---|---|
| `base-url` | `GOOGLE_PLACES_BASE_URL` | `https://places.googleapis.com/v1` (documented endpoint, technical) |
| `api-key` | `GOOGLE_PLACES_API_KEY` | no default; never logged |
| `timeout-seconds` | `GOOGLE_PLACES_TIMEOUT_SECONDS` | D1 |
| `retry-delays-seconds` | `GOOGLE_PLACES_RETRY_DELAYS_SECONDS` | D2 (empty = one attempt) |
| `max-results` / `max-pages` | `GOOGLE_PLACES_MAX_RESULTS` / `..._MAX_PAGES` | D3 |
| `photo-max-width-px` | `GOOGLE_PLACES_PHOTO_MAX_WIDTH_PX` | D16 |
| `language-code`, `region-code` | — | `fr`, `FR` (technical, plan) |
| `fixture-file` | `GOOGLE_PLACES_FIXTURE_FILE` | empty; development only, data NOT real |

`LlmProperties`: the unusable-answer retry count (D11). `OverpassProperties` and `overpass.*` removed.
