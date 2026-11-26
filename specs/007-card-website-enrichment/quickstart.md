# Quickstart: Card Website Enrichment

Validation guide for [spec.md](spec.md). Contract: [contracts/card-enrichment.openapi.yaml](contracts/card-enrichment.openapi.yaml);
entities and outcomes: [data-model.md](data-model.md). Values marked PO-n come from the decisions of [plan.md](plan.md).
Updated for amendment (a): no language model in enrichment; the largest page image rule of spec FR-004. Updated for
amendment (b): Unsplash cuisine photo as the last resort (plan "Amendment (b) (Unsplash)", research R14).

## Prerequisites

- 008 backend in place (this branch) and 008 frontend tasks T062/T063 done (plan "Merge note").
- Local `.env` with `LLM_BASE_URL`, `LLM_API_KEY` (used by the search only, not by enrichment) and
  `GOOGLE_PLACES_API_KEY` (see `.env.example`; never commit values); optional `UNSPLASH_ACCESS_KEY` (Unsplash
  application "Access Key"; blank = no cuisine photo).
- Java 21, Maven, Node (versions of the repository).

## 1. Automated tests

```bash
mvn -f backend test
npm --prefix frontend test -- --watch=false
```

Both must pass (constitution quality gate). Backend tests use recorded HTML pages and a local fake website for
images, redirects, slow answers and refused connections, and the same local server as Unsplash stub; no real site,
no Unsplash and no language model is contacted.

### Test map (spec test plan → where it is proven)

| Spec tests | Proven by |
|---|---|
| T1, T2, T3 | `GooglePlaceMapperTest` (website kept / absent / invalid dropped), `WebsiteAddressTest`, controller 400 test; frontend `deck-input.spec.ts` (008 T060) and swipe-card spec (no link) |
| T4 | `card-enrichment.store.spec.ts`: no request for a restaurant with `photoUrl` |
| T5, T6, T7, T30 | `CardEnrichmentServiceTest` / `WebsitePageReaderTest` with fixture pages (declared image, relative address, broken image, redirect) |
| T8, T10, T11, T12 | `LargestImageRuleTest` (exclusion, area order, no declared size in page order, at most 3), `WebsitePageReaderTest` (T8 and T10 fixture pages), `CardEnrichmentServiceTest` (fake website images) |
| T9 | `CardEnrichmentServiceTest` (largest image broken → second candidate); store spec: preload error → `none`; swipe-card spec: tablecloth |
| T13, T14, T16 | `swipe-card.component.spec.ts`: link present / absent, `target=_blank`, `rel=noopener noreferrer`, pointerdown on the link does not move the card |
| T15, T19, T20, T21, T22, T23 | `deck.component.spec.ts` + store spec: enrichment arriving after display, after a reaction, during a drag, after refinement, after rewind leaves `DeckState` and reactions unchanged |
| T17, T18 | manual run below (SC-001) and a spec checking the deck opens before any enrichment answer |
| T24, T25 | `CardEnrichmentDeadlineTest`: a page answering after 6 s (T24), a page answering in 1 s whose largest image answers after 6 s (T25): result "no photo" in about 5 s, executor free again at once |
| T26, T28 | `CardEnrichmentServiceTest` (connection refused; 4 largest images broken, 5th fine → no photo, only 3 checked) |
| T27 | `EnrichmentWithoutLanguageModelTest`: no enrichment class depends on `llm/`, so no request can reach the model |
| T29 | store spec (one failure, others succeed) |
| T31, T33, T35, T36 | `CardEnrichmentServiceTest` with the local Unsplash stub (photo and credit after `NO_IMAGE`; no photo; no request after a sharing image; no request without key) |
| T32 | no website → 400 before any work (`CardEnrichmentControllerTest`); frontend store: no request without `website` |
| T34, T39 | `UnsplashClientTest` (429, 500, unreachable: no photo, WARN without key; key only in the `Authorization` header), `CardEnrichmentControllerTest` (no key in the response) |
| T37, T38, T42, T43 | `CuisinePhotosTest` (hash position, same website same photo; blank cuisine → "restaurant"; one request in 10 min, two after 61 min with an injected clock) |
| T40, T41 | frontend swipe-card spec (credit shown / not shown), open (tasks.md T062) |
| SSRF (research R5) | `SafeWebFetcherTest`: loopback, private, link-local (`169.254.169.254`), IPv6 local, redirect to a private address, non-http scheme, port per PO-8 |

## 2. Run locally

```bash
mvn -f backend spring-boot:run
npm --prefix frontend start        # http://localhost:4200, /api proxied to :8080
```

### Endpoint checks (curl)

```bash
# A restaurant site declaring og:image: expect {"photoUrl": "...", "photoOrigin": "SHARING_IMAGE"} within 5 s
curl -s "http://localhost:8080/api/card-enrichment?website=https://<a-restaurant-site>/"

# Invalid address: expect 400
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/card-enrichment?website=ftp://example.com"

# Private address: expect 200 {} and an INFO log with outcome BLOCKED_ADDRESS
curl -s "http://localhost:8080/api/card-enrichment?website=http://169.254.169.254/"
curl -s "http://localhost:8080/api/card-enrichment?website=http://localhost:8080/"

# Amendment (b), with UNSPLASH_ACCESS_KEY set: a site without usable image gets a cuisine photo with its credit
# (outcome UNSPLASH_CUISINE_PHOTO); asking again within 1 h sends no new Unsplash request (no new WARN/INFO from
# UnsplashClient, FR-016)
curl -s "http://localhost:8080/api/card-enrichment?website=https://<a-site-without-image>/&cuisine=Restaurant%20italien"

# Unreachable host: expect 200 {} in at most 5 s (outcome UNREACHABLE or TIMEOUT)
time curl -s "http://localhost:8080/api/card-enrichment?website=http://10.255.255.1/"
```

## 3. Acceptance run (product owner)

1. Search "Je veux manger à opera ce midi", press "Commencer".
2. The deck opens with tablecloths; within a few seconds, cards of restaurants with a website switch to a photo
   without the current card moving (US3).
3. Drag a card while its photo arrives: the drag continues (T21).
4. Like a card, rewind to it: it shows its photo (T20, T23).
5. Tap the link icon: the website opens in a new tab, the card does not move; come back: same card, same reactions
   (T15, T16).
6. A card without website shows no link icon (T14).
7. With `UNSPLASH_ACCESS_KEY` set, a card whose website has no usable image shows a cuisine photo with
   "Photo : <photographer> / Unsplash"; both links open a new tab and the card does not move (T31, T40); a website
   photo shows no credit (T41).

## 4. Measurements

- **SC-001**: 3 runs of the reference search with enrichment and 3 with the enrichment requests blocked (browser
  devtools "Block request URL" on `/api/card-enrichment`); time from "Commencer" to the first card (devtools
  Performance or the duration of `POST /api/search` plus render). Means must be equal within noise.
- **SC-004**: 3 runs of the reference search; for each run count deck cards with a website, and among them those
  showing a real photo (target ≥ 50 %). The backend INFO lines (one per enrichment, with its outcome:
  `SHARING_IMAGE`, `LARGEST_PAGE_IMAGE`, `NO_IMAGE`, `NO_DISPLAYABLE_IMAGE`, ...) give the reasons for the misses and
  guide PO-1 and PO-3.
- **SC-005**: for each photo shown, check that `photoUrl` appears in the page source of the restaurant website (after
  redirects).
