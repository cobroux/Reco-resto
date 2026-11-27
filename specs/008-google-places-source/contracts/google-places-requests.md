# Outbound contract: Google Places API (New)

What the backend sends to Google and how it reads the answers. Facts and sources: [research.md](../research.md)
R1, R2, R5, R7. Values marked D# are open decisions of [plan.md](../plan.md).

## Common to every request

- Base: `google-places.base-url` (`https://places.googleapis.com/v1`).
- Headers: `X-Goog-Api-Key: <GOOGLE_PLACES_API_KEY>` (header only, never in a URL), `X-Goog-FieldMask: <mask>`,
  `Content-Type: application/json`.
- `languageCode: "fr"`, `regionCode: "FR"`.
- Connect and read timeout: `google-places.timeout-seconds` (D1). New attempts: `google-places.retry-delays-seconds`
  (D2).
- No request is sent when the key is missing, when the model says the place is outside France, or when the sentence
  is not understood.

## Restaurant search (one per search point, and per page if D3 = Text Search with pages)

Field mask (single constant in the code, FR-008):

```text
places.id,places.displayName,places.location,places.formattedAddress,places.types,places.primaryType,
places.primaryTypeDisplayName,places.businessStatus,places.photos,places.attributions,
places.regularOpeningHours,places.priceLevel,places.websiteUri,places.nationalPhoneNumber,
places.servesVegetarianFood[,nextPageToken]
```

(Address, hours and phone variants per D21/D9; `nextPageToken` only for Text Search with pages.) Billed tier:
Enterprise + Atmosphere (research R2, D8 (4)).

Option Nearby Search (`POST /places:searchNearby`):

```json
{
  "includedTypes": ["<types per D6 and D7>"],
  "maxResultCount": "<D3, 1-20>",
  "locationRestriction": { "circle": { "center": { "latitude": 0, "longitude": 0 }, "radius": 0 } },
  "rankPreference": "<D10: POPULARITY or DISTANCE>",
  "languageCode": "fr",
  "regionCode": "FR"
}
```

Option Text Search (`POST /places:searchText`):

```json
{
  "textQuery": "<built by the backend from the criteria per D7, never written by the model>",
  "includedType": "<one type, optional>",
  "pageSize": "<D3, 1-20>",
  "pageToken": "<from the previous page>",
  "locationBias": { "circle": { "center": { "latitude": 0, "longitude": 0 }, "radius": 0 } },
  "rankPreference": "<D10: RELEVANCE or DISTANCE>",
  "languageCode": "fr",
  "regionCode": "FR"
}
```

The circle radius is the search radius of 002 (distance, else walking minutes, else default), within 0-50,000 m.
Answer: `places[]` (possibly absent = no place) → `GooglePlace` → `Restaurant` ([data-model.md](../data-model.md)).

## Place location (only if D13 chooses a Google locator)

Text Search with `textQuery: <place name>`, `pageSize: 1`, mask `places.location,places.addressComponents`
(country check per D13 (4)). Billed Text Search Pro.

## Photo media (D16)

`GET /{photoName}/media?maxWidthPx=<D16>` with the key header; either follow the redirect and stream the image
(backend address option) or `skipHttpRedirect=true` and use the returned `photoUri` (key-free). `photoName` comes
from the search answer of the same request flow and is never stored (research R5).

## Error mapping (FR-012 option A, FR-013)

Every failure → `SearchException(SERVICE_UNAVAILABLE)` → HTTP 503 `{"code":"SERVICE_UNAVAILABLE"}`. Log line
(WARN): the operation (search, locate, photo), the cause class below, the HTTP status and the Google `status` /
`reason` when present; never the key, never request headers.

| Cause class | Detected by |
|---|---|
| `key-missing` | blank `google-places.api-key` (also one WARN at startup) |
| `key-invalid` | 400 with reason `API_KEY_INVALID` (uncertain, research R7) |
| `permission-denied` | 403 |
| `quota` | 429 / `RESOURCE_EXHAUSTED` |
| `unavailable` | 5xx, connection error |
| `timeout` | read or connect timeout |
| `bad-answer` | unreadable JSON |

For the photo address, the same causes give 503 (or 404 for an unknown/expired photo) and the card shows the
tablecloth.
