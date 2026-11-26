# Implementation Plan: Card Website Enrichment

**Branch**: `feat/card-website-enrichment` (based on `feat/google-places-source`, commit `8dafcf6`) | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-card-website-enrichment/spec.md`

**Status**: Approved (product owner, 2026-11-26): every recommendation of PO-1 to PO-15 accepted, TC-1 to TC-3
accepted. See "Product owner decisions (2026-11-26)". **Amended** the same day by spec amendment (a) (no language
model, largest page image): see "Amendment (a)" below, which prevails over the model-related parts of this plan.
Spec amendment (b) (Unsplash cuisine photo as last resort, FR-006, FR-012 to FR-016, T31 to T43) is planned in
"Amendment (b) (Unsplash)" at the end of this file, which prevails over the earlier sections where they differ.

## Summary

After the deck is shown, the frontend asks the backend, once per restaurant that has a website and no photo, for a
photo taken from that website. The backend reads the website page (redirects followed, http/https only, size cap,
private addresses refused), uses the page's declared sharing image (`og:image`, resolved against the page address)
when the image answers as an image, otherwise sends the page's `<img>` candidates to the existing language model
(`LlmChatClient`), which answers the number of one candidate or none. Everything (page, redirects, image check, model)
fits in one 5 s deadline; past it, or on any failure, the answer is "no photo" and the card keeps the tablecloth with
no error. The frontend keeps the results in a separate store keyed by website, so the deck state, position and
reactions are never touched, and swaps the photo in only once the browser has loaded it. The card front gains a link
icon to the website that opens a new tab without starting a drag. The search itself (`POST /api/search`) is unchanged.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript / Angular 22 (frontend), both existing.

**Primary Dependencies**: Spring Boot 4.1.1 (`RestClient` on the JDK `HttpClient`, `@ConfigurationProperties`,
Jackson 3 `tools.jackson`); existing OpenAI-compatible LLM endpoint through `LlmChatClient` (model `nova-lite`). One
new library proposed: `org.jsoup:jsoup` to parse HTML (TC-1, research R4).

**Storage**: none on the backend (stateless, 002 R6). Frontend: in-memory signal store; kept across searches or not:
PO-13.

**Testing**: JUnit (`mvn -f backend test`), packages mirrored; websites and the LLM faked with `MockRestServiceServer`
or a local test HTTP server for redirects and slow answers (T24, T25, T30); recorded HTML pages as fixtures written
first. Frontend: `*.spec.ts` next to the code (Karma/Jasmine, existing). Opt-in measurement run for SC-001 / SC-004
(quickstart.md).

**Target Platform**: Linux server (Docker on the EC2 `t4g.micro`, 1 GiB RAM, JVM heap 384 MiB) behind nginx on plain
HTTP port 80; Angular SPA.

**Project Type**: web application (backend REST API + Angular frontend), one monorepo.

**Performance Goals**: SC-001: time to the first card unchanged (no work added before the deck). FR-011: each
enrichment ends within 5 s, measured as decided in PO-4. SC-004: at least 50 % of deck cards with a website show a
real photo on the reference search (not measurable before implementation; see Risks).

**Constraints**: SSRF protection on every outbound fetch (research R5); no error shown to the user (FR-012); image
addresses only from the page (FR-006); Google must not bill anything more (no Google request added, research R1);
LLM key server-only.

**Scale/Scope**: POC; up to 15 deck cards per search (`search.deck-size`), so up to 15 enrichment requests and up to
15 model calls per search in the worst case.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|-----------|--------|------|
| I. Spec-Driven Development | Pass, with exception | One branch, one `specs/007-...` folder; spec approved by the product owner (POC exception, as for 008). This plan MUST be approved before `/speckit-tasks`. Specs 002, 003, 008 are not edited. |
| II. Test-First | Pass (to enforce) | Fixture pages, fake LLM answers and slow/redirecting fake sites are written before the code; every T1-T30 test maps to a backend or frontend test (quickstart.md "Test map"). |
| III. Never Guess | **Open** | PO-1 to PO-15 are not filled with assumed values; configuration keys are named, their values are left to the decisions. The only value set is 5 s (spec FR-011). |
| IV. DRY | Pass | Reuse / extend list below; one rule for valid website addresses (research R3); JSON extraction shared (research R7). |
| V. English and Conventional Commits | Pass | |

Post-design re-check (after Phase 1): unchanged. No new project, no persistence; one new library pending review
(TC-1). Gate III stays open until PO-1 to PO-15 are answered.

### DRY report (reuse, extend, create)

| Existing code | Fate | Why |
|---|---|---|
| `LlmChatClient.complete(system, user)` | Reused as is | Same model and endpoint (spec assumption); its blank-answer retries are kept and cut by the 5 s deadline. No timeout added to it (would change 002/008). |
| `SearchAnswerParser.firstObject` (private) | Extracted to a shared helper in `llm/` | The enrichment answer needs the same "first JSON object, whatever surrounds it" reading; `SearchAnswerParser` calls the helper, its tests unchanged. |
| `LlmProperties` | Reused | Model, key, max tokens, retries. |
| `GooglePlaceMapper` (website mapping `text(place.path("websiteUri"))`) | Extended | Uses the single website rule (research R3): invalid addresses are not put in `Restaurant.website`. |
| `Restaurant.website` (backend record) | Reused | Already in the response (008). |
| `@ConfigurationProperties` record + `application.yml` with `${ENV:default}` + `@ConfigurationPropertiesScan` | Pattern reused | New `CardEnrichmentProperties` (`card-enrichment.*`), like `GooglePlacesProperties`. |
| `RestClient.Builder` bean (`AppConfig`, prototype) | Reused | Builds the website client with its own request factory (redirects off, timeouts), as `GooglePlacesClient` does. |
| `PhotoController` pattern (input checked, `ResponseEntity.badRequest()`, no exception body) | Reused | Same style for the 400 on an invalid website. |
| `SearchException` / `ApiExceptionHandler` | Not used by the endpoint | Enrichment failures are never errors for the user (FR-012): the endpoint answers 200 "no photo"; `SearchException(SERVICE_UNAVAILABLE)` from `LlmChatClient` is caught inside the enrichment. |
| Frontend `Restaurant.website`, `deck-input.ts` mapping, `<img>` error → tablecloth | Reused from 008 T060-T063 | Not re-implemented here (research R11). |
| Frontend `SwipeCardComponent` | Extended | Link icon; photo input from the enrichment; pointer handling unchanged except the link stops propagation. |
| Frontend `DECK_TEXTS` | Extended | Accessible name of the link (PO-14). |
| Frontend `DeckStore`, `deck-state.ts` | Not changed | Enrichment lives in a separate store (research R9), so SC-003 holds by construction. |
| `HttpClient` (`provideHttpClient()` in `app.config.ts`) | Reused | Enrichment requests. |

Nothing existing fetches arbitrary web pages, parses HTML or checks addresses for SSRF: these are new.

## Project Structure

### Documentation (this feature)

```text
specs/007-card-website-enrichment/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── card-enrichment.openapi.yaml   # GET /api/card-enrichment (proposed)
├── checklists/
└── tasks.md             # /speckit-tasks, not created here
```

### Source Code (repository root)

```text
backend/pom.xml                                      # + org.jsoup:jsoup (TC-1)
backend/src/main/java/hr/emocio/recoresto/search/
├── api/
│   └── CardEnrichmentController.java                # NEW: GET /api/card-enrichment?website=...
├── config/
│   └── CardEnrichmentProperties.java                # NEW: card-enrichment.* (values: PO decisions)
├── enrichment/                                      # NEW package (name TC-2)
│   ├── CardEnrichmentService.java                   # deadline, sharing image → largest page image, outcome log
│   ├── WebsiteAddress.java                          # single http/https rule (research R3)
│   ├── SafeWebFetcher.java                          # SSRF check per hop, manual redirects, size cap, timeouts
│   ├── WebsitePageReader.java                       # jsoup: og:image, <img> candidates, resolved addresses
│   └── LargestImageRule.java                        # FR-004 steps 2-4 (amendment (a); was ImageChooser, removed)
├── google/GooglePlaceMapper.java                    # extended: WebsiteAddress rule on websiteUri
├── llm/
│   ├── LlmChatClient.java                           # unchanged
│   └── JsonAnswers.java                             # NEW: first JSON object of an answer (moved from SearchAnswerParser)
└── restaurant/SearchAnswerParser.java               # uses JsonAnswers
backend/src/main/resources/application.yml           # card-enrichment section
backend/src/test/java/hr/emocio/recoresto/search/... # mirrors the above; HTML fixtures under src/test/resources

frontend/src/app/
├── enrichment/                                      # NEW
│   ├── card-enrichment.store.ts (+ .spec.ts)        # Map<website, state>, queue, concurrency (PO-10), preload
│   └── card-enrichment.ts                           # response type (contract)
├── deck/deck.component.{ts,html}                    # starts enrichment for the deck candidates; effective photo
├── deck/deck.texts.ts                               # link accessible name
└── deck/swipe-card/swipe-card.component.{ts,html,css,spec.ts}   # link icon; photo binding
```

**Structure Decision**: existing `backend/` and `frontend/`. Backend code stays in `hr.emocio.recoresto.search`
with one sub-package per responsibility, as 002 and 008 did; the endpoint is in `api/` next to `PhotoController`.
Names are proposed for review (TC-2).

## Architecture

```text
Deck shown (FR-009: search and deck opening unchanged)
  CardEnrichmentStore: for each deck candidate with website and no photoUrl (FR-002), current card first,
    at most PO-10 requests at the same time, one per distinct website
    GET /api/card-enrichment?website=<url>
      CardEnrichmentController: WebsiteAddress rule, else 400
      CardEnrichmentService (deadline 5 s, FR-011; task on a bounded executor, cancelled at the deadline)
        1. SafeWebFetcher.page(url): per hop: scheme, port (PO-8), DNS → refuse private/loopback/link-local (R5);
           3xx → next hop (max PO-7); body streamed, cut at PO-1; non-HTML → no photo
        2. WebsitePageReader: og:image (+ PO-5 tags) resolved against final URL / <base>;
           <img> candidates (PO-3), deduplicated, first PO-2
        3. sharing image present → SafeWebFetcher.isImage(url) (200 + image/*) → SHARING_IMAGE   (T5, T6, T27, T30)
           else / not an image (T7) → candidates empty → NO_IMAGE, model not called (T11)
        4. ImageChooser: LlmChatClient.complete → JsonAnswers → {"choice": n|null}
              n in range → MODEL_CHOICE (T8); null → MODEL_DECLINED (T10); other → MODEL_INVALID (T12);
              SearchException → MODEL_UNAVAILABLE (T28)
        deadline reached → TIMEOUT (T24, T25)       any fetch failure → UNREACHABLE / BLOCKED_ADDRESS (T26)
      → 200 {photoUrl, photoOrigin} or 200 {} ; one INFO log line: outcome, duration, host
  Store: 200 with photoUrl → preload in an Image object → on load, photo set for that website (T19-T23);
         anything else → none (FR-012, T9, T29)
  DeckComponent → SwipeCardComponent [photoUrl] = restaurant.photoUrl ?? store.photo(website)
  SwipeCardComponent: link icon <a target=_blank rel=noopener noreferrer>, pointerdown stops propagation (T13-T16)
```

Contract: [contracts/card-enrichment.openapi.yaml](contracts/card-enrichment.openapi.yaml). Entities:
[data-model.md](data-model.md). The search contract of 008 is unchanged (the website rule only removes invalid
values that the spec already excludes).

Technical choices made here because they follow a convention of the code base (mentioned, not asked):
- `RestClient` for outbound HTTP; settings in a `@ConfigurationProperties` record with values in `application.yml`,
  each overridable by a `CARD_ENRICHMENT_*` environment variable.
- Input checked in the controller with a 400 and no body, like `PhotoController`.
- Logs carry the outcome, duration and website host only, never page content (as 008 logs carry ids and counts).
- Frontend: signal-based root service, `OnPush` components, `HttpClient`, texts in `DECK_TEXTS`.

## Decisions needed from the product owner

None of these values is set in this plan. "Rec." is the planner's recommendation, for information only. The spec
leaves order, concurrency and caching to the plan (spec "Assumptions"); they are still listed so the product owner
can accept or change the recommendation.

| # | Decision | Options and consequences | Rec. |
|---|---|---|---|
| PO-1 | Largest part of the page read (`card-enrichment.max-page-bytes`) | Smaller: faster, less memory on a 1 GiB instance, but `<img>` far down long pages are missed (`og:image` is in `<head>`, near the top). Larger: more candidates, more memory per concurrent enrichment (× PO-10). A page over the limit is cut and parsed as is, not rejected. | Cut, not reject; a value around 1 MiB, to be checked against the quickstart outcome counts. |
| PO-2 (obsolete, amendment (a)) | Number of image candidates sent to the model (`card-enrichment.max-candidates`) | More: better chance the good photo is in the list (SC-004), longer prompt and slower answer (5 s budget). Fewer: faster, may miss the photo. First N in document order. | 20, adjusted after measuring. |
| PO-3 | Which `<img>` count as candidates | (a) `src` only; (b) also lazy-loading attributes (`data-src`, `data-lazy-src`) and the largest `srcset` entry; and whether images are dropped before the model when declared smaller than a size, or are `.svg` / `.ico` (logos, icons). (b) + dropping tiny/vector images raises SC-004 and shortens the prompt; the size threshold is a value to give. | (b), drop `.svg`/`.ico` and images declared under 100 px wide or high. |
| PO-4 | Where the 5 s is measured, and the frontend's own limit | (a) backend: 5 s from receiving the request; the browser also gives up after a safety limit (value to give) in case the backend is stuck. (b) browser: 5 s from sending, backend budget slightly shorter (value to give) to answer in time. Queued requests (PO-10) are not counted in either. | (a), browser safety limit 7 s. |
| PO-5 | Which declared sharing images count (FR-003) | (a) `og:image` (and its `secure_url` / `url` variants) only; (b) also `twitter:image`, then `<link rel="image_src">` as fallbacks before the model. (b) avoids some model calls. | (b), in that order. |
| PO-6 | How the backend presents itself to websites | `User-Agent`: an honest bot name (e.g. "RecoResto/0.1 (+contact URL)", some sites block it) or a browser-like value (fewer blocks, less honest). `robots.txt`: honored (one more request per site, inside the 5 s) or not. | Honest name with the project URL; `robots.txt` not read (one page per user action, like a link preview). |
| PO-7 | Maximum number of redirects followed (T30) | 1 to ~10. More: reaches booking platforms with chains of redirects; each hop costs time and is checked (R5). | 5. |
| PO-8 | Ports accepted for websites and images | (a) default ports only (80, 443): safer against internal services; a site on another port gets no photo; (b) any port. | (a). |
| PO-9 | Protection against abuse of the public endpoint | Anybody reaching `/api/*` can make the backend fetch public pages and spend model calls. (a) nothing for the POC (same as the 008 photo endpoint); (b) a per-IP request limit (value to give; adds code or nginx config). | (a) for the POC, with (b) before any public announcement. |
| PO-10 | Concurrency | (1) Browser: how many enrichment requests at the same time. Browsers allow about 6 connections per origin over HTTP/1.1, shared with the search and the `/api/photos` images, so a high value delays those. (2) Backend: size of the enrichment executor (requests over it wait, and their wait counts in the 5 s under PO-4 (a)). | Browser 3; backend 8. |
| PO-11 (obsolete, amendment (a)) | What a "suitable" image is (model instructions) | E.g. a photo of the dining room, the front or a dish; never a logo, icon, map, menu scan, text banner or portrait. The wording decides SC-004 and SC-005-adjacent quality. | The list above. |
| PO-12 | How the image reaches the browser (research R8) | (a) direct address from the website, with `referrerpolicy="no-referrer"`: no backend load, no new SSRF surface; the user's browser contacts the restaurant site or its CDN (privacy), some hotlink-protected sites fail → tablecloth; if the app moves to HTTPS, `http` images may fail → tablecloth. (b) backend proxy: hides the user, robust to hotlink and mixed content; needs a signed token or a server-side cache (no raw-URL proxy), and every image transits the 1 GiB instance (another size limit to give). | (a). |
| PO-13 | Keeping results | (1) Frontend: results kept for the session across searches (same website → no new request) or only for the current deck. (2) Backend cache (would add state and memory): none or short in-memory. | Frontend: whole session; backend: none. |
| PO-14 | Link icon on the card front (FR-007) | Place (e.g. top-left corner of the photo area, opposite the distance badge at top-right; or next to the name) and accessible name (e.g. "Site du restaurant"). Must not overlap the 008 "Google Maps" attribution (008 T065). | Top-left of the photo area; "Site du restaurant". |
| PO-15 | Meaning of "found on the website" (FR-006, SC-005) | Images are often served from another host (a CDN such as the site builder's). (a) any image address referenced by the page counts, whatever its host; (b) only images on the website's own host (many sites would then have no candidate). | (a). |

## Technical choices to confirm at review

| # | Choice | Note |
|---|---|---|
| TC-1 | Add `org.jsoup:jsoup` to `backend/pom.xml` | Research R4; alternative: regular expressions (no library, fragile). |
| TC-2 | Names: endpoint `GET /api/card-enrichment?website=`, package `search.enrichment`, classes listed above, frontend folder `app/enrichment` | Proposed, as 008 proposed its package names. GET chosen: no side effect, cacheable by the browser. |
| TC-3 | Invalid website values are no longer put in `Restaurant.website` by `GooglePlaceMapper` | Research R3; edits 008 code (not its spec); output only changes for values the 007 spec says must show no link. |

## Risks

- SC-004 (50 %) is not measured: many home pages declare a logo as `og:image`, and the model only sees addresses and
  `alt` texts, not the images. Levers: PO-3, PO-11, a multimodal choice (research R7, out of this plan).
- Sharing image is a logo: FR-003 uses it as is (the spec takes the declared image without judging it).
- DNS rebinding between the address check and the connection: closed by the PR #15 review fix (research R5).
- Cancelling the LLM call at the deadline relies on the JDK `HttpClient` aborting on interrupt; to be proven by T25's
  test. If not, the request thread finishes later in the background (no user impact, one executor slot held).
- Load on the `t4g.micro`: up to 15 enrichments per search, each holding a page buffer (PO-1) and a thread.
- Model cost and rate: up to 15 model calls per search when no site declares a sharing image.
- Image rights: accepted by the team for the POC (spec).

## Merge note (008)

007 is built on 008 (`8dafcf6`). Its frontend depends on 008 T062 (`website` in the frontend `Restaurant` and
`deck-input.ts`) and T063 (`<img>` error → tablecloth), still open. 007 frontend tasks MUST start after them and
MUST NOT re-implement them; 007 backend tasks can start at once. The link icon must be placed together with the
008 "Google Maps" attribution (008 T064/T065): `swipe-card.component.*` is a shared file.

## Blocking `/speckit-tasks`

1. Approval of this plan (constitution I; POC exception as for 008).
2. Answers to PO-1 to PO-15 (constitution III).
3. Review of TC-1 to TC-3.

## Complexity Tracking

No constitution violation to justify.

## Product owner decisions (2026-11-26)

"Ok tout valider": every recommendation is accepted as written.

- PO-1: a page over the limit is cut and parsed; `card-enrichment.max-page-bytes` = 1 MiB.
- PO-2: ~~`card-enrichment.max-candidates` = 20 (first 20 in document order).~~ Obsolete (amendment (a)): key removed, every page image is a candidate.
- PO-3: option (b): `src`, `data-src`, `data-lazy-src` and the largest `srcset` entry; `.svg` and `.ico` dropped; images declared under 100 px wide or high dropped.
- PO-4: option (a): 5 s measured by the backend from receiving the request; the browser gives up after 7 s.
- PO-5: option (b): `og:image` (and `og:image:secure_url` / `og:image:url`), then `twitter:image`, then `<link rel="image_src">`.
- PO-6: honest User-Agent naming the project with its URL; `robots.txt` not read.
- PO-7: at most 5 redirects.
- PO-8: ports 80 and 443 only.
- PO-9: no abuse protection for the POC; a per-IP limit is required before any public announcement.
- PO-10: browser 3 enrichment requests at the same time; backend executor of 8.
- PO-11: ~~suitable image = a photo of the dining room, the front or a dish; never a logo, icon, map, menu scan, text banner or portrait.~~ Obsolete (amendment (a)): no model prompt; replaced by the rule of spec FR-004.
- PO-12: option (a): direct image address with `referrerpolicy="no-referrer"`; no backend proxy.
- PO-13: frontend keeps results for the whole session (keyed by website); no backend cache.
- PO-14: link icon at the top-left of the photo area, accessible name "Site du restaurant", not overlapping the 008 "Google Maps" attribution.
- PO-15: option (a): any image address referenced by the page counts as found on the website, whatever its host.
- TC-1 (jsoup), TC-2 (names), TC-3 (mapper drops invalid website values): accepted.
- Order: 008 frontend tasks (T060-T066) are done first, then the 007 tasks.

### Answers to the open questions of tasks.md (product owner, 2026-11-26)

- OQ-1: jsoup `1.24.1` (latest release on Maven Central, `maven-metadata.xml` of 2026-11-03), pinned.
- OQ-3: one candidate per `<img>`: the largest `srcset` entry, else `data-src`, else `data-lazy-src`, else `src`.
- OQ-4: ~~the restaurant name is sent to the backend: the contract gets a `name` query parameter (optional, used only in the model prompt), next to `website`.~~ Obsolete (amendment (a)): the `name` parameter is removed from the contract, the controller and the service.
- OQ-2: User-Agent `RecoResto/0.1 (proof of concept)` (no URL).

## Amendment (a) (product owner, 2026-11-26): no language model

Spec amendment (a): the LLM key is limited to 1,500 tokens per minute and one model call per card (about 1,200
tokens) would block the search itself. Enrichment no longer calls the language model; when the website declares no
usable sharing image, the largest page image is chosen by the fixed rule of spec FR-004 (values confirmed by the
product owner on 2026-11-26). This section prevails over the model-related parts above.

**Flow** (replaces steps 3-4 of "Architecture"):

```text
3. sharing image present and SafeWebFetcher.isImage(url) → SHARING_IMAGE                     (T5, T6, T30)
   else / not an image (T7) ↓
4. LargestImageRule (FR-004 steps 2-4) on the WebsitePageReader candidates (step 1):
     exclude "logo" / "icon" in address or alt text, any case;
     order by declared area (width x height; srcset width descriptor as the width), largest first;
       no declared area after, in page order; keep at most 3
     nothing left → NO_IMAGE                                                                   (T10, T11)
5. check them in order with SafeWebFetcher.isImage; first displayable → LARGEST_PAGE_IMAGE      (T8, T9, T12)
   none displayable → NO_DISPLAYABLE_IMAGE                                                     (T28)
deadline 5 s covers page, sharing image and page image checks → TIMEOUT                       (T24, T25)
```

**Decisions**

- Kept: PO-1, PO-3 (now FR-004 step 1), PO-4 to PO-10, PO-12 to PO-15, OQ-1 to OQ-3, TC-1 to TC-3.
- Obsolete: **PO-2** (`max-candidates` removed: every page image is a candidate, since the largest may be anywhere in
  the page), **PO-11** (no prompt), **OQ-4** (the `name` parameter is removed end to end).
- "At most 3 checked" (FR-004 step 4) is a spec value, kept as a constant of `LargestImageRule`, not a setting.
- Outcomes: `MODEL_CHOICE`, `MODEL_DECLINED`, `MODEL_INVALID`, `MODEL_UNAVAILABLE` are replaced by
  `LARGEST_PAGE_IMAGE` (photo) and `NO_DISPLAYABLE_IMAGE`; `photoOrigin` is `SHARING_IMAGE` or `LARGEST_PAGE_IMAGE`.
- OQ-5 answered (product owner, 2026-11-26): reading (a), as implemented (research R13): the `srcset` width
  descriptor replaces the `width` attribute and is multiplied by the declared `height`; an image with only one of the
  two has no declared area and goes after, in page order. Spec FR-004 step 3 carries the clarification.

**DRY report (amendment (a))**

| Code | Fate |
|---|---|
| `ImageChooser`, `ImageChoice`, prompt (PO-11), model answer parsing, `ImageChooserTest` | Removed |
| `LargestImageRule` (+ test) | Created: the single place of FR-004 steps 2-4 |
| `SafeWebFetcher.isImage` | Reused for the page image checks (same rule as the sharing image, FR-003) |
| `WebsitePageReader` | Extended: keeps the `srcset` width descriptor; no candidate limit; page title no longer read |
| `ImageCandidate`, `WebsitePage` | Changed: `number` and `title` removed (model only), `srcsetWidth` added |
| `CardEnrichmentProperties`, `application.yml` | `max-candidates` removed |
| `CardEnrichmentController`, `CardEnrichmentService` | `name` parameter removed; no dependency on `llm/` (T27) |
| `LlmChatClient` | Unchanged; no longer used by enrichment |
| `llm/JsonAnswers` | Kept: still used by `SearchAnswerParser` |

**Risks (updated)**: "Model cost and rate" and "Cancelling the LLM call" no longer apply. New: the declared size is
not the real size (many sites declare none, or use CSS); the page order then decides (T12). SC-004 still unmeasured.

## Amendment (b) (Unsplash)

Spec amendment (b) (product owner, 2026-11-26): when neither the sharing image nor a page image gives a displayable
photo, the card shows a free Unsplash photo of the restaurant cuisine, with its credit (spec US1 AS7-AS8, FR-006,
FR-012 to FR-016, T31 to T43). It replaces the Pexels option (key issuance paused). This section prevails over the
earlier sections where they differ. Research: R14. Entities: data-model.md "Card enrichment", "Photo credit",
"Cuisine photo cache". Contract: `cuisine` parameter, `UNSPLASH_CUISINE_PHOTO` origin and `credit` object.

**Flow** (after step 5 of "Amendment (a)"):

```text
GET /api/card-enrichment?website=<url>&cuisine=<Restaurant.cuisine>     (cuisine optional, sent by the frontend)
  ... steps 1-5 of amendment (a) unchanged ...
6. website outcome NO_IMAGE or NO_DISPLAYABLE_IMAGE (the page was read, FR-003 and FR-004 gave nothing)
   and an Unsplash access key is configured (T36), and budget left:
     query = cuisine trimmed, inner blanks collapsed; blank or absent → "restaurant" (T38)
     CuisinePhotos: cache hit (same normalized query, younger than 1 h) → no request (T42); else          (FR-016)
     UnsplashClient: GET {base-url}/search/photos?query=<query>&per_page=15&lang=fr                       (FR-013)
                     Authorization: Client-ID <key>, Accept-Version: v1; timeout = remaining budget (FR-011)
       429 / 4xx / 5xx / unreachable / unreadable → UNSPLASH_UNAVAILABLE, one WARN line (status or cause class
       only, never the key nor the query string), not cached (T34, T39)
     usable results = the first 15 results whose urls.regular and user.links.html are absolute https addresses
       and whose user.name is not blank, in answer order
     none → NO_CUISINE_PHOTO (T33)
     else photo = usable[ floorMod(website.hashCode(), usable.size()) ] → UNSPLASH_CUISINE_PHOTO (T31, T37)
   other website outcomes (UNREACHABLE, BLOCKED_ADDRESS, NOT_HTML, TIMEOUT): no Unsplash request (OQ-6, T26)
→ 200 {photoUrl, photoOrigin: UNSPLASH_CUISINE_PHOTO, credit: {photographerName, photographerUrl, unsplashUrl}}
```

**Architecture**

- `CardEnrichmentController`: new optional `cuisine` query parameter, passed as is to the service; the website rule
  and the 400 are unchanged. The frontend sends `Restaurant.cuisine` (the text shown on the card, for example
  "Restaurant italien").
- `CardEnrichmentService`: after `NO_IMAGE` / `NO_DISPLAYABLE_IMAGE`, asks `CuisinePhotos` when it is enabled
  (key present); the Unsplash step runs inside the same executor task and the same 5 s deadline (FR-011).
- `CuisinePhotos` (new, `enrichment/`): query normalization, 1 h cache, photo choice, credit links. In-memory
  `ConcurrentHashMap` keyed by the normalized query (lower case, `Locale.ROOT`); an entry is the answer of one
  request and the instant the request was sent (injected `Clock`, the `AppConfig` bean); it is used while
  `now < sentAt + cache-ttl`. One request per query at a time: concurrent enrichments of the same cuisine wait for the
  answer in flight (each within its own remaining budget) instead of sending their own, so a deck of 15 Italian
  restaurants costs one request. Failures are not cached (the next card asks again). Expired entries are removed
  when a new request is stored. Only photo addresses and credits are kept, never images (spec assumption).
- Photo position (FR-013, T37): `Math.floorMod(website.uri().toString().hashCode(), usable.size())`. The hash is
  `String.hashCode` of the website address exactly as received by the endpoint (after the `WebsiteAddress` trim);
  its formula is fixed by the Java language specification, so the same website always gets the same position across
  requests, restarts and machines; `floorMod` makes it non-negative.
- Credit (FR-015): `photographerName` = `user.name`; `photographerUrl` = `user.links.html` plus
  `utm_source=<app-name>&utm_medium=referral` (appended with `&` when the address already has a query; fragment
  dropped); `unsplashUrl` = `https://unsplash.com/?utm_source=<app-name>&utm_medium=referral`. The app name is
  URL-encoded.
- `UnsplashClient` (new, `enrichment/`): the fixed trusted Unsplash API, not routed through `SafeWebFetcher` (no
  SSRF check: the host comes from configuration, not from third parties); `RestClient` from the `AppConfig` builder
  on the JDK `HttpClient` with the remaining budget as read timeout per request (as `SafeWebFetcher` does). Key in the
  `Authorization` header only (FR-014, T39); without key no request and one WARN at startup (as `GooglePlacesClient`).
  Returned addresses are hotlinked (`urls.regular`, spec assumption): the browser loads them from the Unsplash image
  host; `urls.regular` must be `https` (a non-https or unparseable address is not usable).
- `EnrichmentOutcome`: new `UNSPLASH_CUISINE_PHOTO` (photo), `NO_CUISINE_PHOTO`, `UNSPLASH_UNAVAILABLE`.
- `CardEnrichment`: new optional `credit` (`PhotoCredit`), present exactly when `photoOrigin` is
  `UNSPLASH_CUISINE_PHOTO`.
- Frontend (open, tasks.md T061-T063): send `cuisine`; show "Photo : <photographer> / Unsplash" with both links in a
  new tab, without reaction nor drag (T40); no credit for a website photo (T41).

**Configuration** (`card-enrichment.unsplash.*`, `CardEnrichmentProperties.Unsplash`)

| Key | Value | Source |
|---|---|---|
| `base-url` | `https://api.unsplash.com` (`CARD_ENRICHMENT_UNSPLASH_BASE_URL`) | Unsplash documentation |
| `access-key` | `${UNSPLASH_ACCESS_KEY:}`, no default; blank = no Unsplash step | spec Assumptions, FR-014 |
| `app-name` | `reco_resto` (`CARD_ENRICHMENT_UNSPLASH_APP_NAME`) | spec FR-015 |
| `per-page` | 15 (`CARD_ENRICHMENT_UNSPLASH_PER_PAGE`) | spec FR-013 ("the first 15 results") |
| `cache-ttl` | `1h` (`CARD_ENRICHMENT_UNSPLASH_CACHE_TTL`) | spec FR-016 |

`lang=fr` is a spec value (FR-013), kept as a constant of `UnsplashClient`, not a setting.

**DRY report (amendment (b))**

| Code | Fate |
|---|---|
| `CardEnrichmentProperties` + `application.yml` `${ENV:default}` pattern | Extended: nested `unsplash` record, key masked in `toString` (as `GooglePlacesProperties`) |
| `RestClient.Builder` (`AppConfig`), JDK request factory with remaining budget | Reused (same per-request timeout as `SafeWebFetcher`) |
| `Clock` bean (`AppConfig`) | Reused for the cache age |
| `JsonMapper` bean (Jackson 3) | Reused to read the Unsplash answer |
| `Deadline`, executor task, `TIMEOUT` | Reused: the Unsplash step is inside the existing 5 s task |
| `FetchException` (outcome + message) | Reused to carry `UNSPLASH_UNAVAILABLE` / `TIMEOUT` |
| `EnrichmentOutcome`, `CardEnrichment`, outcome INFO line | Extended |
| `GooglePlacesClient` failure log style (operation, status / cause class, never the key) | Followed |
| `SafeWebFetcher` | Not used for Unsplash (trusted fixed API host) |
| `FakeWebsite` (test) | Reused as the local Unsplash stub; records the query string too |
| `UnsplashClient`, `CuisinePhotos`, `PhotoCredit` | Created |

**Risks**: the demo limit (50 requests per hour) counts distinct cuisines per hour, not cards; above it, cards keep
the tablecloth (spec assumption). `lang` is a beta parameter of Unsplash that must be enabled for the application
(OQ-8). The cuisine text comes from the browser: anybody reaching `/api/*` can spend the Unsplash quota with
distinct queries (same exposure as PO-9).

**Open questions for the product owner (not decided here)**

- **OQ-6**: should a website that cannot be read (`UNREACHABLE`, `BLOCKED_ADDRESS`, `NOT_HTML`) also get the
  cuisine photo? Spec AS7 and FR-013 say "when FR-003 and FR-004 give no displayable photo", but T26 (Expected:
  tablecloth for a refused connection) was not changed by the amendment. Implemented: no Unsplash request for these
  outcomes (T26 kept); only `NO_IMAGE` and `NO_DISPLAYABLE_IMAGE` lead to Unsplash. `TIMEOUT` never does (no budget
  left).
- **OQ-7**: the Expected of T10, T11 and T28 (and AS3 / FR-005) say "tablecloth"; with a key configured these cases
  now get an Unsplash photo (T31). Their Expected should be restated by the product owner ("tablecloth when Unsplash
  gives no photo"). The backend tests of T10, T11 and T28 run without key.
- **OQ-8**: Unsplash documents `lang` as a beta parameter that must be enabled by email to api@unsplash.com with the
  application id; until then the French query may be searched without French handling. `lang=fr` is sent anyway
  (FR-013). Team action: ask Unsplash, or accept.
- **OQ-9**: the Unsplash guidelines ask to call `links.download_location` when the application does something
  "similar to a download" (for example a user inserting a photo in a post). Showing a photo on a card is treated as a
  display, not a download: no tracking request is sent. To be confirmed.
- **OQ-10**: `.github/workflows/deploy.yml` writes `~/.env` with an explicit list of keys; `UNSPLASH_ACCESS_KEY` must
  be added there and as a GitHub secret (not done: CI/CD change left to the team), else production has no key and
  the Unsplash step is skipped.
- **OQ-5** (amendment (a)): answered, see "Amendment (a)".
