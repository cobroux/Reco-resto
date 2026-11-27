# Data Model: Card Website Enrichment

Builds on [008 data-model](../008-google-places-source/data-model.md). "PO-n" and "TC-n" refer to
[plan.md](plan.md). Updated for amendment (a) (2026-11-26): no language model; the page image is chosen by the
largest image rule (spec FR-004). Updated for amendment (b) (2026-11-26):
Unsplash cuisine photo as the last resort (spec FR-013 to FR-016, plan "Amendment (b) (Unsplash)", research R14).

## Restaurant (response of `POST /api/search`) - unchanged shape

| Field | Rule (007) |
|---|---|
| website | Present only when Google gives `websiteUri` AND it passes the website rule below (FR-001, FR-002, T1-T3, TC-3). |
| photoUrl | Unchanged (008). When present, no enrichment is requested (FR-002, T4). |

Frontend deck `Restaurant` (`frontend/src/app/restaurant/restaurant.ts`): gains `website?` through 008 T062 (not
redefined here).

## Website address (backend value object, `WebsiteAddress`)

| Rule | Detail |
|---|---|
| Absolute URI | parsed with `java.net.URI`; relative or unparseable → invalid |
| Scheme | `http` or `https` (case-insensitive) |
| Host | present and non-blank |
| User-info | absent (`user:pass@host` → invalid) |
| Port | accepted per PO-8 |

Single authoritative rule (research R3): used by `GooglePlaceMapper` and by the enrichment endpoint.

## Card enrichment request (`GET /api/card-enrichment?website=`)

| Field | Type | Rule |
|---|---|---|
| website | string | required; must pass the website rule, else 400 |
| cuisine | string, optional | `Restaurant.cuisine` as shown on the card (amendment (b)); only used as the Unsplash query; blank or absent → query "restaurant" (T38) |

The `name` parameter of OQ-4 is removed (amendment (a): it only fed the model prompt).

## Card enrichment (response; spec "Key Entities")

| Field | Type | Rule |
|---|---|---|
| photoUrl | string (absolute URI), optional | an address found in the page (FR-006): the resolved sharing image or the largest page image; or the `urls.regular` address of an Unsplash photo (`https` only, hotlinked); absent when no photo |
| photoOrigin | `SHARING_IMAGE` \| `LARGEST_PAGE_IMAGE` \| `UNSPLASH_CUISINE_PHOTO`, optional | present exactly when `photoUrl` is present |
| credit | Photo credit, optional | present exactly when `photoOrigin` is `UNSPLASH_CUISINE_PHOTO` (FR-015, T40, T41) |

### Photo credit (amendment (b), FR-015)

| Field | Type | Rule |
|---|---|---|
| photographerName | string | Unsplash `user.name`, not blank |
| photographerUrl | string (absolute `https` URI) | Unsplash `user.links.html` + `utm_source=<app-name>&utm_medium=referral` |
| unsplashUrl | string (absolute `https` URI) | `https://unsplash.com/?utm_source=<app-name>&utm_medium=referral` |

The card shows "Photo : <photographerName> / Unsplash", each name linking to its address (frontend, T40).

Under PO-12 (b) `photoUrl` would be a backend address instead; the shape stays the same.

## Website page (backend, internal, never returned)

| Field | Type | Rule |
|---|---|---|
| finalUrl | URI | address after redirects (T30); base for relative addresses together with `<base href>` |
| sharingImage | URI, optional | first non-blank of the declared sharing-image tags (PO-5), resolved (T6) |
| candidates | list of Image candidate | from `<img>` (FR-004 step 1, PO-3, OQ-3), page order, deduplicated by address, all of them (no limit: PO-2 obsolete) |

### Image candidate

| Field | Type | Rule |
|---|---|---|
| url | URI | absolute, `http`/`https` only; `data:` and others dropped |
| alt | string, optional | `alt` attribute, trimmed |
| width, height | int, optional | declared attributes when numeric |
| srcsetWidth | int, optional | width descriptor (`1200w`) of the `srcset` entry the address comes from |

## Largest image rule (backend, internal, `LargestImageRule`; spec FR-004 steps 2-4)

| Step | Rule |
|---|---|
| 2 | Candidates whose `url` or `alt` contains "logo" or "icon", any case, are excluded (T8, T10) |
| 3 | Ordered by declared area, largest first: width x height, the width being `srcsetWidth` when present, else `width`; a candidate without width or without height has no declared area and comes after, in page order (T12); equal areas keep page order |
| 4 | The first 3 are checked in that order with the image check (200 + `image/*`); the first displayable one is the photo (`LARGEST_PAGE_IMAGE`, T9); none → `NO_DISPLAYABLE_IMAGE` (T28) |

No language model, no image search service (T27).

## Cuisine photo cache (backend, internal, `CuisinePhotos`; amendment (b), FR-016)

| Field | Rule |
|---|---|
| key | the Unsplash query (cuisine trimmed, inner blanks collapsed, "restaurant" when blank) in lower case |
| photos | the usable photos of the answer, in answer order, at most `per-page` (15): `urls.regular` and `user.links.html` absolute `https`, `user.name` not blank |
| sentAt | instant the request was sent (injected `Clock`) |

An entry is used while `now < sentAt + cache-ttl` (1 h, T42, T43); a failed request is not stored; memory only, lost
on restart. Photo of a card: `photos[floorMod(website.uri().toString().hashCode(), photos.size())]` (T37).

## Enrichment outcome (backend, log only)

`SHARING_IMAGE`, `LARGEST_PAGE_IMAGE` (photo returned); `NO_IMAGE` (no candidate left by the rule, T10, T11),
`NO_DISPLAYABLE_IMAGE` (the checked candidates all fail, T28), `UNREACHABLE`, `BLOCKED_ADDRESS`, `NOT_HTML`,
`TIMEOUT` (no photo). Amendment (b): `UNSPLASH_CUISINE_PHOTO` (photo), `NO_CUISINE_PHOTO` (Unsplash answered no
usable photo, T33), `UNSPLASH_UNAVAILABLE` (429, other status, unreachable, unreadable answer, T34); asked only after
`NO_IMAGE` or `NO_DISPLAYABLE_IMAGE` with a key configured (plan OQ-6). A page over PO-1 is
cut and parsed, not rejected (if PO-1 decides otherwise, a `TOO_LARGE` outcome is added). Logged once per request with duration and website host (research R12).

State flow of one enrichment:

```text
request ─► website rule ✗ ─► 400
        ─► fetch page (hops ≤ PO-7, each hop SSRF-checked) ─ blocked ─► BLOCKED_ADDRESS
                                                            ─ error / non-2xx ─► UNREACHABLE
                                                            ─ not HTML ─► NOT_HTML
        ─► sharing image? ─ yes ─► image check 200 + image/* ─ ok ─► SHARING_IMAGE
                          │                                  ─ ✗ ─┐ (T7)
                          └ no ─────────────────────────────────┤
        ─► largest image rule: nothing left ─► NO_IMAGE (T10, T11)  ◄┘
        ─► check ≤ 3 candidates in order ─ first image ok ─► LARGEST_PAGE_IMAGE (T8, T9, T12)
                                          ─ none ok ─► NO_DISPLAYABLE_IMAGE (T28)
        ─► NO_IMAGE / NO_DISPLAYABLE_IMAGE and Unsplash key (amendment (b)):
              cache or GET /search/photos ─ usable photo ─► UNSPLASH_CUISINE_PHOTO (T31, T37, T42)
                                          ─ none ─► NO_CUISINE_PHOTO (T33)
                                          ─ 429 / error / unreachable ─► UNSPLASH_UNAVAILABLE (T34)
              no key ─► the website outcome, no request (T36)
   at any point: deadline 5 s ─► TIMEOUT
```

## Card enrichment state (frontend, `CardEnrichmentStore`)

Keyed by website address string (chains share one entry; rewind reads the same entry, T23).

| State | Meaning | Card shows |
|---|---|---|
| (absent) | not requested yet | tablecloth |
| `queued` | waiting for a free slot (PO-10) | tablecloth |
| `pending` | request sent or image preloading | tablecloth (no indicator, spec assumption) |
| `photo(url)` | image loaded by the browser | the photo (with the Unsplash credit when the answer has one, T40) |
| `none` | no photo, error, timeout (PO-4) or image load error (T9) | tablecloth |

Transitions: absent → queued → pending → photo | none. Terminal states are kept for the deck, or for the session
(PO-13). The deck state (`DeckState`) is never written by this store (SC-003).

Effective photo of a card: `restaurant.photoUrl ?? photo(website)`; the data-source photo always wins (T4).

## Configuration (`CardEnrichmentProperties`, `card-enrichment.*`)

| Key | Value |
|---|---|
| `budget-seconds` | 5 (spec FR-011) |
| `max-page-bytes` | 1 MiB (PO-1) |
| `min-image-size-px` | 100 (PO-3, FR-004 step 1) |
| `max-redirects` | 5 (PO-7) |
| `allowed-ports` | 80, 443 (PO-8) |
| `user-agent` | `RecoResto/0.1 (proof of concept)` (PO-6, OQ-2) |
| `executor-size` | 8 (PO-10 (2)) |

| `unsplash.base-url` | `https://api.unsplash.com` (research R14) |
| `unsplash.access-key` | `UNSPLASH_ACCESS_KEY`, no default; blank = no Unsplash step (T36); never logged |
| `unsplash.app-name` | `reco_resto` (FR-015) |
| `unsplash.per-page` | 15 (FR-013) |
| `unsplash.cache-ttl` | 1 h (FR-016) |

`lang=fr` (FR-013) is a constant of `UnsplashClient`.

`max-candidates` (PO-2) is removed by amendment (a). The "at most 3 checked" of FR-004 step 4 is a spec value, kept
as a constant of `LargestImageRule`, not a setting.

Frontend constants (next to the store): concurrency PO-10 (1), safety timeout PO-4.
