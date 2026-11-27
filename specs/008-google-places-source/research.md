# Research: Google Places Restaurant Source

Sources read on 2026-11-25. Google states that its pages may change and that the binding terms are the team's Google
Maps Platform agreement; every fact on terms and prices below is **to be confirmed by the team**. Items marked
**(uncertain)** were not found stated in the documentation.

## R1 - Search endpoints (Text Search vs Nearby Search)

Sources: https://developers.google.com/maps/documentation/places/web-service/text-search ,
https://developers.google.com/maps/documentation/places/web-service/nearby-search

| | Text Search (New) | Nearby Search (New) |
|---|---|---|
| Endpoint | `POST https://places.googleapis.com/v1/places:searchText` | `POST https://places.googleapis.com/v1/places:searchNearby` |
| Headers | `X-Goog-Api-Key`, `X-Goog-FieldMask` (required; error without it) | same |
| Query | `textQuery` (required, free text) | none: types only |
| Types | `includedType` (one type), `strictTypeFiltering` | `includedTypes` / `excludedTypes` / `includedPrimaryTypes` / `excludedPrimaryTypes`, up to 50 each, Table A |
| Area | `locationBias` circle (radius 0-50,000 m) or rectangle (results may be outside); `locationRestriction` rectangle only | `locationRestriction` circle, radius > 0 and ≤ 50,000 m (strict) |
| Results | `pageSize` 1-20 (default 20); `pageToken`; at most 60 over all pages ("subject to change"); later pages must repeat the same parameters | `maxResultCount` 1-20 (default 20); no pagination |
| Ranking | `rankPreference` `RELEVANCE` or `DISTANCE` (categorical queries) | `POPULARITY` (default) or `DISTANCE` |
| Other | `openNow`, `priceLevels`, `minRating`, `languageCode` (default `en`), `regionCode` | `languageCode`, `regionCode` |

- **Decision**: open (plan D3). Both are usable behind `RestaurantSource`.
- **Rationale for listing both**: Nearby matches the radius rule exactly and filters by several cuisine types in
  one request; Text Search reaches 60 results (FR-017) and accepts cuisine words but only biases the area.
- **Consequence common to both**: one request per search point (several places → several requests, run in
  parallel, then merged by `DeckSelector`). `openNow` is not used: it only covers "now" and would drop places with
  unknown hours, which 002 keeps (D9); the backend filter stays the single rule (DRY).

## R2 - Field mask and billing tier (FR-008)

Sources: same pages (field-to-SKU lists), https://developers.google.com/maps/billing-and-pricing/pricing

The billed SKU is the highest tier among the requested fields.

| 002 FR-012 data | Google field | Tier (Text / Nearby) |
|---|---|---|
| place identifier | `places.id` | Essentials IDs only (Text) / Pro (Nearby) |
| name | `places.displayName` | Pro |
| location | `places.location` | Pro |
| address | `places.formattedAddress` (or `shortFormattedAddress`, D21) | Pro |
| cuisine type | `places.types`, `places.primaryType`, `places.primaryTypeDisplayName` | Pro |
| photo (+ author) | `places.photos` (name, authorAttributions, googleMapsUri) | Pro |
| closed / open status | `places.businessStatus` | Pro |
| data attributions | `places.attributions` | Essentials IDs only / Pro |
| weekly opening hours | `places.regularOpeningHours` (or `currentOpeningHours`, D9) | Enterprise |
| price level | `places.priceLevel` | Enterprise |
| website | `places.websiteUri` | Enterprise |
| phone | `places.nationalPhoneNumber` (or international, D21) | Enterprise |
| diets | `places.servesVegetarianFood` | **Enterprise + Atmosphere** |
| keywords, signature dish, menu | not provided | stays absent (FR-011) |

Prices (first tier, per 1,000 requests, USD) and free monthly caps:

| SKU | Price | Free / month |
|---|---|---|
| Text Search Pro / Nearby Search Pro | 32.00 | 5,000 |
| Text Search Enterprise / Nearby Search Enterprise | 35.00 | 1,000 |
| Text Search / Nearby Search Enterprise + Atmosphere | 40.00 | 1,000 |
| Place Details Photos | 7.00 | 1,000 |
| Geocoding | 5.00 | 10,000 |

- **Decision**: the field mask is the list above (FR-008, product owner: the fields of 002 FR-012 that Google
  provides). Resulting tier: **Enterprise + Atmosphere** because of `servesVegetarianFood`; without it, Enterprise.
  Confirmation requested in D8(4).
- `places.rating` would not change the tier (Enterprise) but is out of scope for display; its use for ordering is
  D10.
- Whether a request that fails (4xx/5xx) is billed: **(uncertain)**, not stated on the pages read.

## R3 - Place types (D6, D7, D8)

Source: https://developers.google.com/maps/documentation/places/web-service/place-types (Table A, "Food and Drink")

Restaurant-like types include `restaurant` and about 120 `*_restaurant` types (e.g. `italian_restaurant`,
`asian_restaurant`, `chinese_restaurant`, `japanese_restaurant`, `thai_restaurant`, `vietnamese_restaurant`,
`korean_restaurant`, `indian_restaurant`, `french_restaurant`, `pizza_restaurant`, `sushi_restaurant`,
`ramen_restaurant`, `fast_food_restaurant`, `fine_dining_restaurant`, `vegetarian_restaurant`, `vegan_restaurant`,
`halal_restaurant`), plus non-`_restaurant` food places: `bistro`, `diner`, `gastropub`, `bar_and_grill`, `cafe`,
`cafeteria`, `bar`, `pub`, `wine_bar`, `bakery`, `deli`, `food_court`, `meal_takeaway`, `meal_delivery`,
`sandwich_shop`, `kebab_shop`, `noodle_shop`, `pizza_delivery`, `salad_shop`, `snack_bar`, `tea_house`, etc. Diet
types: `vegetarian_restaurant`, `vegan_restaurant`, `halal_restaurant`; no kosher or gluten-free type.

- **Decision**: open (D6 which types are restaurants, D7 how cuisines map to types, D8 diet indicators). Whatever
  the answers, the tables live in one class (`GooglePlaceTypes`) and the model prompt is generated from it when D7 =
  closed list (single source).

## R4 - Place data shapes (mapping, data-model.md)

Source: https://developers.google.com/maps/documentation/places/web-service/reference/rest/v1/places

- `PriceLevel`: `PRICE_LEVEL_UNSPECIFIED`, `PRICE_LEVEL_FREE`, `PRICE_LEVEL_INEXPENSIVE`, `PRICE_LEVEL_MODERATE`,
  `PRICE_LEVEL_EXPENSIVE`, `PRICE_LEVEL_VERY_EXPENSIVE` → D5.
- `BusinessStatus`: `BUSINESS_STATUS_UNSPECIFIED`, `OPERATIONAL`, `CLOSED_TEMPORARILY`, `CLOSED_PERMANENTLY`,
  `FUTURE_OPENING` → D9(3).
- `OpeningHours.periods[]`: `open` / `close` points with `day` (0 = Sunday … 6 = Saturday), `hour`, `minute`
  (place local time); start day not fixed; empty array = never open; a place open 24 h has one `open` (day 0, 00:00)
  and no `close`. `weekdayDescriptions` is localized text (not used: the deck needs periods).
- `Photo`: `name` (`places/{placeId}/photos/{photo}`), `widthPx`, `heightPx`, `authorAttributions[]`
  (`displayName`, `uri`, `photoUri`), `flagContentUri`, `googleMapsUri`.
- `attributions[]`: `provider`, `providerUri` (third-party data providers to show with the result).
- `displayName` and `primaryTypeDisplayName` are `LocalizedText` (`text`, `languageCode`), in the request language
  when available.
- `location`: `latitude`, `longitude`.

- **Decision (technical)**: `GoogleOpeningHours` converts periods to the existing `Map<DAY, List<OpeningPeriod>>`
  so that `RestaurantFilters.isOpen` and the frontend stay unchanged; a period over midnight is split into
  "opens → end of day" and "start of next day → closes"; a 24 h place gives every day `00:00`-`00:00` ("closing not
  after opening = until end of day", existing `OpeningPeriod` rule).

## R5 - Photos (FR-010, FR-013, D16)

Source: https://developers.google.com/maps/documentation/places/web-service/place-photos

- Media: `GET https://places.googleapis.com/v1/{photoName}/media?maxWidthPx=…&maxHeightPx=…` with the key (query
  `key` or the `X-Goog-Api-Key` header); `maxWidthPx`/`maxHeightPx` 1-4,800, at least one required.
- Default: HTTP redirect to the image. `skipHttpRedirect=true`: JSON `{name, photoUri}`; the documented `photoUri`
  contains no key. Lifetime of `photoUri`: **(uncertain)**, not documented.
- "You cannot cache a photo name. Also, the name can expire": a photo name is taken from a fresh search and used
  right away.
- Billed as Place Details Photos ($7 / 1,000, 1,000 free) per media request (from the pricing page; the photo page
  does not name the SKU).
- **Decision**: open (D16). Both options keep the key on the server. With the backend address option, the backend
  validates the path parameters (identifier characters only) so that it can only call the Google photo media
  endpoint (no open proxy to other hosts), and an error gives an HTTP error so that the `<img>` falls back to the
  tablecloth (FR-010).

## R6 - Terms: attribution, caching, EEA (FR-014 to FR-016)

Sources: https://developers.google.com/maps/documentation/places/web-service/policies ,
https://developers.google.com/maps/comms/eea/places ,
https://cloud.google.com/terms/maps-platform/eea-places-api-permitted-uses

- Attribution without a Google map: Google Maps logo preferred (height 16-19 dp, clear space 10 dp left/right/top,
  5 dp bottom, unmodified, accessibility label "Google Maps"); text "Google Maps" accepted when space is limited
  (12-16 sp, weight 400, not translated, not wrapped, `translate="no"`); near the top or bottom of the content, in
  the same visual container. Third-party providers from `attributions` must be shown with "Google Maps".
- Photos: "You must always credit the author when displaying photos"; author name and link (and avatar when space
  allows); thumbnails may omit it if a larger view shows it; each photo must link to its source on Google Maps
  (`googleMapsUri`). → D15.
- Caching: place IDs may be stored indefinitely; latitude/longitude at most 30 consecutive calendar days; other
  content only as the service-specific terms allow, and "don't copy and save business names, addresses, or user
  reviews". → no persistence (D4 a), and logs keep place IDs, not names/addresses (plan, technical choice).
- EEA (billing address in the EEA, terms of 8 July 2025): Places API content must not be used "With any Map",
  except latitude, longitude and place ID; "With any Map" covers content shown on, beside or visually associated
  with a map. The Places UI Kit is named by Google as the alternative for content next to a map. → FR-015 (005/006
  deferred by the product owner).

## R7 - Errors and the key (FR-012, FR-013)

Source: Google API error model (https://cloud.google.com/apis/design/errors), Places pages above.
**(uncertain)**: the exact status/reason pairs below are the standard Google API ones and were not all read on a
Places page; tests use recorded or documented bodies.

| Situation | Typical answer | Outcome |
|---|---|---|
| no key configured | (no call made) | `SERVICE_UNAVAILABLE`; log "Google Places API key missing" |
| invalid key | 400 `INVALID_ARGUMENT` with reason `API_KEY_INVALID` | `SERVICE_UNAVAILABLE`; log "invalid key" |
| key not allowed / API disabled / billing off | 403 `PERMISSION_DENIED` | `SERVICE_UNAVAILABLE`; log reason |
| quota exceeded | 429 `RESOURCE_EXHAUSTED` | `SERVICE_UNAVAILABLE`; log "quota" |
| server error | 5xx | `SERVICE_UNAVAILABLE` (new attempts: D2) |
| timeout / connection error | — | `SERVICE_UNAVAILABLE` (timeout: D1) |
| malformed answer | unreadable JSON | `SERVICE_UNAVAILABLE` |
| no place | 200 with no `places` | empty list → `NO_RESULT` (002) |

- **Decision (technical)**: the key is sent in the `X-Goog-Api-Key` header, never in a URL (so it cannot appear in
  a logged URL), and log lines are built from the status and the reason only, never from request headers.

## R8 - Language model without tools (FR-002, FR-004 to FR-007)

- **Decision**: one `LlmChatClient.complete(system, user)` call, no tools. The prompt of `OverpassRestaurantSource`
  is moved and edited: no Overpass, no tag filters; cuisines expressed per D7; centers optional with coordinates.
- **Rationale**: FR-002 forbids model-written data-source arguments; the tool loop was the source of the invented
  tag filters and of several LLM round trips (~1 s each). One call meets SC-001 more easily.
- **Robust reading (technical, within FR-004-FR-006)**: strip code fences; scan the text for balanced `{…}`
  objects (string-aware) and parse them; keep the one(s) with an `understood` field (which one when several: D12);
  missing `criteria` → empty criteria; missing `moment`/distance/cuisines → 002 defaults (`SearchCriteria` and
  `MomentResolver` already do it); invalid center coordinates → place without position (located, D13).
- **Unreadable** (no object, invalid JSON, empty): a distinct state, never "not understood"; new attempts per D11,
  then `SERVICE_UNAVAILABLE` (FR-007). `understood=false` read from a valid object stays `NOT_UNDERSTOOD`.
- **Alternatives considered**: JSON mode / `response_format` of the endpoint (not verified on the team's endpoint,
  002 O8; can be added later without changing the reading rules); keeping tool calling with a Google tool
  (rejected: FR-002).

## R9 - Locating places (FR-006, D13)

- The model's coordinates are free and fast but not validated (002 O12). Google Places Text Search with
  `places.location` (and `addressComponents` to check the country) locates landmarks well (Pro tier); Google
  Geocoding is cheaper but address-oriented; API Adresse (`api-adresse.data.gouv.fr`, free, France only) was used in
  002 before the pivot (`ApiAdresseClient`, deleted; can be restored from git history rather than rewritten).
- **Decision**: open (D13). `PlaceLocator` hides the choice from `SearchService`.

## R10 - Device position (D14)

- Observed in the code: `SearchService.search` never reads `request.position()`; the frontend sends it
  (`http-search.service.ts`). 002 FR-006 requires it when the sentence has no place; 002 O15 left the "how" open.
- **Decision**: open (D14), recommendation: the backend uses it as the only search point when the answer names no
  place.

## R11 - Frontend impact

- `swipe-card.component.html` renders `<img [src]="photo">` without an error handler: a photo that fails to load
  shows a broken image, not the tablecloth (FR-010). **Decision (technical)**: on `error`, the card falls back to the
  tablecloth.
- `deck-input.ts` builds the deck id from `name|address`; with Google it may use `placeId` when present
  (technical, no visible change).
- nginx (`frontend/nginx.conf`) and the dev proxy (`proxy.conf.json`) already forward `/api/`, so a relative
  `/api/photos/...` address works in both (D16 a).
- The attribution element (D15) is a new visible element of the card front (spec allows it: FR-014).
