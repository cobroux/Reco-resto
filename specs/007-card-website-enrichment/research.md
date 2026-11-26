# Research: Card Website Enrichment

Phase 0 of [plan.md](plan.md). Every technical choice below is given as Decision / Rationale / Alternatives. Values
that the spec does not give are NOT chosen here: they are marked "PO-n" and listed in plan.md, "Decisions needed from
the product owner". Facts about the code base were read on branch `feat/card-website-enrichment` at commit `8dafcf6`.

**Amendment (a) (2026-11-26)**: the language model is no longer used by enrichment (spec amendment (a), FR-004).
R7 is superseded by R13; R1, R2, R4, R6 and R12 are updated below.

**Amendment (b) (2026-11-26)**: Unsplash cuisine photo as the last resort (spec FR-013 to FR-016): R14.

## R1 - Where the enrichment runs: backend endpoint called per card by the frontend

- **Decision**: a backend endpoint, called by the frontend once per restaurant with a website, after the deck is
  shown (contract: [contracts/card-enrichment.openapi.yaml](contracts/card-enrichment.openapi.yaml)). The search
  (`POST /api/search`) is not changed and does not wait (FR-009).
- **Rationale**:
  - The browser cannot read another site's HTML (CORS), so it cannot find `og:image` itself.
  - One request per card gives the "each card is updated as soon as its own enrichment is done" behavior (FR-010,
    US3 AS2) and isolates failures (FR-012, T29) without any server-push mechanism.
- **Alternatives considered**:
  - Enrich inside `POST /api/search`: rejected, it delays the deck (FR-009, SC-001).
  - One batch endpoint for the whole deck with streaming (SSE): rejected, a new transport for the code base, and a
    slow website would hold the stream; per-card requests are simpler and already independent.
  - The backend looks the website up itself from `placeId` (Google Place Details): rejected, an extra billed Google
    request per card, against the PO rule "Google must not bill anything" (008 plan, 2026-11-25). The website is
    already in the search response (008 `Restaurant.website`).

## R2 - Input of the endpoint: the website address sent by the browser

- **Decision**: the frontend sends the website address it received in the search response. The backend re-validates
  it with the same rule as the search response (R3) and never trusts it further (R5).
- **Rationale**: stateless backend (002 R6, 008 D4); no Google call.
- **Consequence**: anybody who reaches `/api/*` can make the backend fetch a public web page and spend a
  few image checks (at most 4 image requests: the sharing image and 3 page images). This is the same exposure class as the 008 photo endpoint (008 plan "Risks"). Rate limiting: PO-9.

## R3 - One rule for "valid website address" (FR-002, T3)

- **Decision**: a single backend rule: absolute URI, scheme `http` or `https`, a host present, no user-info. It is
  applied (a) in `GooglePlaceMapper` when building `Restaurant.website`, so an invalid value never reaches the
  response, and (b) by the enrichment endpoint (400 on an invalid value). The frontend only checks presence of
  `website` (link shown, enrichment requested).
- **Rationale**: DRY (constitution IV): the frontend does not re-implement URI validation; the edge case "malformed
  or not http/https: no enrichment and no link" then holds by construction. This changes FR-001's output only for
  invalid values, which the spec already says must not be shown.
- **Alternatives**: validating in the frontend as well (two rules to keep in sync); rejected.

## R4 - Reading the page

- **Decision**: one HTTP GET of the website address, redirects followed manually (each hop re-checked by R5), body
  read as a stream and cut at a size limit (PO-1), parsed as HTML. Only this page is read (spec assumption "home
  page only").
- **Sharing image**: `<meta property="og:image">` (also `og:image:secure_url`, `og:image:url`), first one with a
  value, resolved against the final page address (after redirects) and the page's `<base href>` when present (T6).
  Other "declared sharing image" tags (`twitter:image`, `<link rel="image_src">`): PO-5.
- **"Can be displayed" (FR-003, T7)**: the backend requests the sharing image (R5 rules apply) and accepts it only on
  HTTP 200 with a `Content-Type` starting with `image/`. The body is not decoded (decoding would cost memory and time
  on a 1 GiB instance); a well-typed image that still fails in the browser falls back to the tablecloth on the
  frontend (008 T063, spec edge case).
- **Image candidates (FR-004)**: `<img>` elements of the page, in document order, with their address resolved like
  the sharing image, deduplicated; `data:` addresses and non-http(s) addresses are dropped (they are not addresses
  "found on the website" that a card can load). Which extra attributes are read (`srcset`, lazy-loading `data-src`)
  and whether small or decorative images are dropped: PO-3, OQ-3. Since amendment (a) every candidate is kept (no
  PO-2 limit) and the largest image rule (R13) picks among them.
- **HTML parser**: no HTML parser is in `backend/pom.xml` today. Options: (a) add `org.jsoup:jsoup` (tolerant HTML5
  parser, resolves relative URLs with `absUrl`, handles charset); (b) regular expressions on the raw text (no new
  library, fragile on real sites: attribute order, quotes, comments, `<script>` content). **Recommendation: (a)**;
  it is a new dependency, so it is listed for team approval (plan "Technical choices to confirm at review", TC-1).
- **HTTP client**: a `RestClient` built on the JDK `HttpClient` with redirects disabled and a per-request timeout
  taken from the remaining budget (R6). This follows the code base convention (`GooglePlacesClient`, `LlmChatClient`
  use `RestClient`); `exchange()` gives access to the body stream for the size cut.
- **Request headers**: `User-Agent` value and whether `robots.txt` is honored: PO-6. `Accept: text/html`.

## R5 - SSRF protection (fetching addresses chosen by third parties)

The backend fetches addresses that come from Google data, from the browser (R2) and from third-party HTML (redirects,
`og:image`). It runs on EC2 (instance metadata at `169.254.169.254`) in a Docker network where `backend` and
`frontend` resolve to private addresses (`docker-compose.yml`).

- **Decision**: before every connection (first request, every redirect hop, sharing-image check), the host is
  resolved and the request is refused when any resolved address is: loopback, any-local (`0.0.0.0`, `::`),
  site-local / private (10/8, 172.16/12, 192.168/16), link-local (169.254/16, fe80::/10, which covers the EC2
  metadata address), carrier-grade NAT (100.64/10), unique-local IPv6 (fc00::/7), multicast, or an IPv4-mapped IPv6
  form of these. Only schemes `http` / `https`. A refused hop ends the enrichment with no photo (no error to the
  user, FR-012). The ports accepted: PO-8.
- **Residual risk**: DNS rebinding between the check and the connection (the JDK client resolves again). Pinning
  the connection to the checked address is not possible with the JDK client and TLS SNI without a custom socket
  layer; accepted for the POC and recorded in plan "Risks". EC2 IMDSv2 (token by PUT) further limits the metadata
  risk; GET-only fetches cannot obtain the token.
- **Redirect limit**: number of hops followed: PO-7 (spec T30 requires at least one).
- **Alternatives**: an allow-list of hosts (impossible: any restaurant site); an outbound HTTP proxy with its own
  filtering (new infrastructure, rejected for the POC).

## R6 - The 5 s budget (FR-011, T24, T25)

- **Decision**: one deadline per enrichment, started when the backend receives the request, covering page fetch,
  redirects, sharing-image check and the page image checks (amendment (a)). Implementation: the enrichment runs as a task on a bounded
  executor; the controller waits for it with a timeout equal to the remaining budget, then cancels it (interrupt) and
  answers "no photo". Each HTTP request of the page and image fetch uses the remaining budget as its timeout, so a
  slow website never holds a socket beyond the deadline.
- **Image checks** (amendment (a), replaces the former "Language model" point): each image check is a website
  request, so it gets the remaining budget as timeout like the page fetch, and the deadline also cancels the task.
  T25 is a page answering in 1 s whose largest image answers after 6 s: no photo at 5 s, and the executor thread is
  free again at once (`CardEnrichmentDeadlineTest`).
- **Where the 5 s is measured and what the frontend does**: PO-4 (backend-side 5 s and the browser's own safety
  timeout).
- **Executor size** (how many enrichments the backend runs at the same time): PO-10.

## R7 - Asking the language model to choose (FR-004, FR-006, T8, T10, T12) — SUPERSEDED by R13 (amendment (a))

*Kept for history only. The model is no longer called by enrichment: the LLM key is limited to 1,500 tokens per
minute and one call per card (about 1,200 tokens) would block the search itself (spec amendment (a)). `ImageChooser`,
its prompt (PO-11) and its answer parsing are removed; the `name` parameter (OQ-4) is removed. `llm/JsonAnswers`
stays: `SearchAnswerParser` still uses it.*

- **Decision**: reuse `LlmChatClient.complete(system, user)` (same model and endpoint as 002/008, spec assumption).
  The user message lists the candidates as numbered lines: number, absolute address, `alt` text, declared width and
  height when present, plus the restaurant name and the page title. The model answers one JSON object
  `{"choice": <number>}` or `{"choice": null}`.
- **Rationale**: answering a number makes FR-006 hold by construction: the backend maps the number back to the
  candidate list. Any other answer (out-of-range number, an address, text without a readable object) is rejected and
  gives no photo (T12, T10). No repair attempt: the 5 s budget does not allow it.
- **JSON extraction**: `SearchAnswerParser.firstObject` (private) already reads the first complete JSON object
  whatever text or code fence surrounds it (008 FR-004, D12). **DRY**: it is moved to a small shared helper in the
  `llm` package and used by both parsers; `SearchAnswerParser` behavior and tests are unchanged.
- **Model availability**: `LlmChatClient` throws `SearchException(SERVICE_UNAVAILABLE)`; the enrichment catches it
  and ends with no photo (T28). The model is only called when no usable sharing image exists, so T27 (sharing image
  with model down) never reaches it.
- **Alternatives**: sending the images themselves to a multimodal model (Nova Lite accepts images): rejected for the
  POC, downloading N images and a larger model call do not fit in 5 s on this instance; noted as a lever if SC-004
  is not met. Answering the address: rejected (needs exact string match and allows invented addresses).
- **Prompt wording**: what "suitable" means (photo of the room, the front or a dish; not a logo, icon, map, banner
  text or person portrait) is product wording: PO-11.

## R8 - How the image reaches the browser

Options weighed:

| | (a) Direct address (recommended) | (b) Backend proxy |
|---|---|---|
| What the card loads | the image address found on the website | `/api/...` on the backend, which fetches the image |
| Mixed content | The app is served over plain HTTP today (`nginx.conf` listens on 80, no TLS in `docker-compose.yml`), so `http` images load. If the app moves to HTTPS, browsers auto-upgrade `http` images to `https` and drop them when that fails → tablecloth (008 T063). | none: same origin |
| Hotlink protection | Most protections check the `Referer` header; the `<img>` gets `referrerpolicy="no-referrer"`. Sites refusing requests without referer → tablecloth. | none: the backend sends no referer |
| SSRF / open proxy | none added: the browser fetches, not the server | the endpoint must not accept a raw URL (that is an open proxy). Needs either a server-signed token (HMAC with a new secret) or a server-side cache of found addresses (state, against 002 R6) |
| Backend load | none | every image passes through a 1 GiB `t4g.micro`; needs an image size cap (another value) |
| Privacy | the user's browser contacts the restaurant site or its CDN (IP address, user agent) | only the backend contacts third parties |
| FR-006 / SC-005 | address shown = address found | same |

- **Recommendation**: (a), with `referrerpolicy="no-referrer"` and the existing load-error fallback. Choice: PO-12
  (it has a privacy consequence).

## R9 - Frontend: updating the card in place (FR-010, SC-003, T19-T23)

- **Decision**: a new root service `CardEnrichmentStore` holds a signal `Map<website, result>` (`pending`, `photo`,
  `none`). The deck state (`DeckState`, `DeckStore`) is NOT modified: reactions, position, plan and refinement stay
  untouched by construction (SC-003). `DeckComponent` passes to `SwipeCardComponent` an effective photo:
  `restaurant.photoUrl ?? enrichmentPhoto(restaurant.website)`. The card is a signal-input `OnPush` component tracked
  by `restaurant.id`, so a new photo only changes the `<img>` binding; its drag signals (`dx`, `dy`, `start`) are not
  reset (T21).
- **Which restaurants, in which order**: at deck start, every restaurant of `DeckInput.restaurants` with a website
  and no photo (FR-002, T4); the current card first, then deck order. Cards entering through refinement or any later
  candidate list are requested when first seen (T22); since the store is keyed by website, a rewound card shows the
  result that arrived while hidden (T23) and chains share one request (spec edge case "several restaurants share the
  same website": each card gets the same photo). How many requests at the same time: PO-10. Kept between searches or
  not: PO-13.
- **Preloading**: the photo is swapped in only after the browser loaded it (`Image` object `load` event); a load
  error keeps the tablecloth. This avoids a broken-image flash and covers T9 without an error shown. It also covers
  the frontend side of T7 when the image passed the backend check but cannot be decoded.
- **Failure**: any non-200, network error or timeout → `none`, nothing shown (FR-012). No loading indicator (spec
  assumption).
- **Alternatives**: writing the photo into the `Restaurant` objects of `DeckState` (would create new objects in the
  deck state and make SC-003 depend on every state function preserving references): rejected.

## R10 - Website link on the card front (FR-007, FR-008, T13-T16)

- **Decision**: an `<a href target="_blank" rel="noopener noreferrer">` with a link icon (inline SVG, as the default
  dish icon already is), on the card front, shown only when `website` is present. Its `pointerdown` stops
  propagation so `SwipeCardComponent.onPointerDown` never starts a drag or captures the pointer (T16); its `click`
  only opens the tab (no reaction, T15). `draggable="false"` on the link. Accessible name from `DECK_TEXTS`
  (French UI texts live there). Exact place on the card and accessible name: PO-14.
- **Deck kept after the tab** (T15): the deck lives in root services (`DeckStore`, `SearchResultStore`); opening a
  new tab does not navigate the app, so position and reactions are kept without further work.

## R11 - Dependency on 008 frontend tasks

The frontend has not received 008 yet: `frontend/src/app/search/search-result.ts` and
`frontend/src/app/restaurant/restaurant.ts` have no `website`; 008 tasks T060-T063 (optional fields mapped in
`deck-input.ts`, deck id from `placeId`, `<img>` error → tablecloth) are open. 007 relies on them and must not
re-create them (DRY): 007 frontend tasks start after 008 T062 and T063 are done (plan "Merge note").

## R12 - Observability for SC-004

- **Decision**: one INFO log line per enrichment with the outcome (`SHARING_IMAGE`, `LARGEST_PAGE_IMAGE`,
  `NO_IMAGE`, `NO_DISPLAYABLE_IMAGE`, `UNREACHABLE`, `BLOCKED_ADDRESS`, `NOT_HTML`, `TIMEOUT`, and since amendment (b)
  `UNSPLASH_CUISINE_PHOTO`, `NO_CUISINE_PHOTO`, `UNSPLASH_UNAVAILABLE`; see data-model.md) and the duration, with the website host only (no page content). The quickstart counts outcomes to
  measure SC-004 and to tune PO-1/PO-3. The outcome is not part of the HTTP response beyond the photo origin.

## R13 - Largest page image by a fixed rule (amendment (a); FR-004, FR-006, T8-T12, T25, T27, T28)

- **Decision**: when no usable sharing image exists, `LargestImageRule` applies FR-004 steps 2-4 to the candidates
  read by `WebsitePageReader` (step 1, R4): exclude candidates whose address or `alt` contains "logo" or "icon" (any
  case); order by declared area, largest first, candidates with no declared area after, in page order; keep at most
  3. `CardEnrichmentService` checks them in that order with the existing image check (`SafeWebFetcher.isImage`: 200
  + `image/*`) and uses the first that passes (`LARGEST_PAGE_IMAGE`).
- **Declared area**: width x height, the width being the width descriptor of the chosen `srcset` entry when there is
  one, else the `width` attribute; without a width or a height there is no declared area. This reading was confirmed
  by the product owner on 2026-11-26 (tasks.md OQ-5, reading (a)) and added to spec FR-004 step 3.
- **Rationale**: FR-006 holds by construction (the photo is one of the page's own image addresses); no token, no
  model rate limit; at most 3 page image requests per card, inside the 5 s budget (R6).
- **No model**: no enrichment class depends on `llm/` (`EnrichmentWithoutLanguageModelTest`, T27).
- **Alternatives**: the model choice (R7, superseded); downloading the images to measure their real size (memory and
  time on the 1 GiB instance); image search APIs (not used by amendment (a)).

## R14 - Unsplash cuisine photo (amendment (b); FR-006, FR-012 to FR-016, T31-T43)

Sources read on 2026-11-26: https://unsplash.com/documentation (search photos, authorization, rate limits) and
https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines.

- **Request**: `GET https://api.unsplash.com/search/photos?query=<cuisine>&per_page=15&lang=fr`, headers
  `Authorization: Client-ID <access key>` and `Accept-Version: v1`. `per_page` defaults to 10 and is capped at 30, so
  15 is accepted. The key is never sent as the `client_id` query parameter (it would appear in URLs and logs, FR-014).
- **Answer**: `results[]`, each with `urls.regular` (the card image, hotlinked), `user.name` (photographer),
  `user.links.html` (photographer profile), `links.html` (photo page, not used by the card), `links.download_location`
  (download tracking, see below). Only the fields used are read; anything else is ignored.
- **Limits**: demo applications get 50 requests per hour, counted on `api.unsplash.com` JSON requests only (image
  loads from the image host do not count). The 1 h cache keyed by the normalized query (FR-016) turns this into 50
  distinct cuisines per hour; one request per query at a time (concurrent enrichments of the same cuisine share the
  answer in flight) so a deck of the same cuisine does not spend several requests.
- **Guidelines**: hotlink `photo.urls.*` (no copy, no proxy: the browser loads `urls.regular`); credit the
  photographer and Unsplash, linking to the photographer profile, links back to Unsplash with
  `?utm_source=<app name>&utm_medium=referral` (FR-015, app name `reco_resto` by default); keep the access key
  secret (backend only, FR-014); do not use the Unsplash name for the application (Reco-Resto does not).
- **`lang`**: documented as a beta parameter that Unsplash enables per application on request (email to
  api@unsplash.com with the application id). It is sent as FR-013 asks; whether it is honored is plan OQ-8.
- **Download tracking**: the guidelines ask to call `links.download_location` when the application does something
  similar to a download (inserting a photo in a post, setting it as a header). Showing a photo on a card is a display;
  no tracking request is sent (plan OQ-9).
- **Trust**: `api.unsplash.com` is a fixed host from configuration, not an address chosen by third parties, so the
  client does not go through `SafeWebFetcher` (R5). The returned image and profile addresses come from Unsplash and
  reach the browser: they are accepted only when absolute `https` addresses (no `javascript:`, no `http`).
- **Choice** (FR-013, T37): among the usable results of the first 15, the one at
  `Math.floorMod(website.hashCode(), count)`; `String.hashCode` is specified by the Java language, so the position is
  stable across restarts and machines.
- **Failures** (FR-012, T34): 429 (limit), any other status, an unreachable host, an unreadable answer or the budget
  spent give no photo; one WARN line with the status or the cause class only (never the key, the headers nor the
  query string, T39).
- **Alternatives**: Pexels (key issuance paused, spec amendment (b)); Brave image search and Google Street View
  (paid, excluded by the spec); a backend image proxy (against the hotlinking guideline).
