# Tasks: Card Website Enrichment

**Input**: Design documents from `/specs/007-card-website-enrichment/` (spec.md, plan.md, research.md, data-model.md,
contracts/card-enrichment.openapi.yaml, quickstart.md)

**Prerequisites**: spec.md (Approved 2026-11-26, test plan T1-T30 with every Expected filled), plan.md (Approved,
"Product owner decisions (2026-11-26)": PO-1 to PO-15, TC-1 to TC-3 accepted).

**Tests**: required (constitution II, spec test plan). Every behavior has a test task naming the spec test ids it
implements; the test is written first and MUST fail before the implementation task that follows it.

**Amendment (a) (2026-11-26)**: no language model in enrichment; the largest page image is chosen by the rule of
spec FR-004 (plan.md "Amendment (a)"). Tasks T013 and T019 are superseded; T002, T003, T009, T012, T014-T016,
T018, T020 and T021 were adapted by the Phase 3a tasks T044-T050 below. 
**Amendment (b) (2026-11-26)**: Unsplash cuisine photo as the last resort (spec FR-006, FR-012 to FR-016, T31 to
T43; plan.md "Amendment (b) (Unsplash)"): Phase 3b, tasks T051-T063.

**Organization**: the backend (endpoint, SSRF-safe fetch, page reading, largest image rule, 5 s deadline) is built and
validated first and can start at once. The frontend phases start only after 008 T062, T063 and T065 are done and
this branch is rebased on them (plan "Merge note", research R11); they MUST NOT re-implement them.

**Context constraints**

- Values come only from plan.md "Product owner decisions (2026-11-26)" (cited as PO-n / TC-n), the spec (5 s,
  FR-011) and research.md (cited as R#). A value not found there is listed under "Open questions" at the end and
  the task that needs it is blocked on it; never choose it.
- DRY (constitution IV): reuse or extend what the plan "DRY report" and "Amendment (a)" name (`LlmChatClient`
  unchanged and not used by enrichment, `SearchAnswerParser.firstObject` moved to `llm/JsonAnswers`, `RestClient.Builder` of `AppConfig`, the
  `PhotoController` input style, the `@ConfigurationProperties` pattern of `GooglePlacesProperties`).
- `DeckState`, `deck-state.ts` and `DeckStore` are NOT changed (research R9, SC-003 by construction).
- The search (`POST /api/search`) and its contract are unchanged, except TC-3 (invalid website values dropped).
- Do not touch `.claude/` or `.env*` files. No commit unless asked.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1..US3 of spec.md
- Backend paths are relative to `backend/src/main/java/hr/emocio/recoresto/search/` (main) and
  `backend/src/test/java/hr/emocio/recoresto/search/` (test) unless written in full. Frontend paths are written in
  full.

---

## Phase 1: Setup (dependency and configuration)

**Purpose**: the HTML parser and the `card-enrichment.*` settings exist before any code uses them.

- [X] T001 [P] Add `org.jsoup:jsoup` to `backend/pom.xml` (TC-1, research R4), version per Open question OQ-1
  (blocked until answered). `mvn -f backend test` still green.
- [X] T002 [P] Write `config/CardEnrichmentPropertiesTest.java` (test): binds every key of data-model.md
  "Configuration" with the PO values: `budget-seconds` 5 (FR-011), `max-page-bytes` 1 MiB = 1048576 (PO-1),
  ~~`max-candidates` 20 (PO-2)~~ (removed by T049), `min-image-size-px` 100 (PO-3), `max-redirects` 5 (PO-7), `allowed-ports` 80 and 443
  (PO-8), `user-agent` (PO-6, value per OQ-2), `executor-size` 8 (PO-10 (2)); each key overridable by its
  `CARD_ENRICHMENT_*` environment variable. Follow `config/GooglePlacesPropertiesTest.java`.
- [X] T003 Create `config/CardEnrichmentProperties.java` (`@ConfigurationProperties("card-enrichment")` record,
  registered like `GooglePlacesProperties`) and add the `card-enrichment:` section to
  `backend/src/main/resources/application.yml` with the values of T002, each `${CARD_ENRICHMENT_*:default}` and one
  comment per value citing its decision (PO-n / FR-011). `user-agent` blocked on OQ-2. Makes T002 pass.

**Checkpoint**: `mvn -f backend test` passes; the application starts with the new section.

---

## Phase 2: Foundational (backend, blocking all stories)

**Purpose**: the single website rule, the shared JSON reader and the SSRF-safe fetcher used by every story.

- [X] T004 [P] Write `enrichment/WebsiteAddressTest.java` (test, T3, FR-002): per data-model.md "Website address"
  (absolute URI, scheme, host, user-info, port): `https://x.fr/` and `HTTP://x.fr` valid; `ftp://…`, `monresto`,
  relative, unparseable, no host, `user:pass@host` invalid; explicit port other than 80/443 invalid (PO-8).
- [X] T005 Create `enrichment/WebsiteAddress.java`: the single http/https rule of research R3 (used by
  `GooglePlaceMapper` and the endpoint). Makes T004 pass.
- [X] T006 [P] Write `llm/JsonAnswersTest.java` (test, research R7 DRY): first complete JSON object read whatever
  prose or code fence surrounds it; two objects → the first; braces inside strings handled; no object → empty.
- [X] T007 Create `llm/JsonAnswers.java` by moving the private `SearchAnswerParser.firstObject` (no behavior change)
  and make `restaurant/SearchAnswerParser.java` call it. `restaurant/SearchAnswerParserTest.java` unchanged and
  green. Makes T006 pass.
- [X] T008 [P] Write `enrichment/SafeWebFetcherTest.java` (test, research R5, R4, PO-1, PO-6, PO-7, PO-8; part of
  T26, T30): with a local fake website (JDK `HttpServer`) and an injectable address check (the test allows loopback
  explicitly; the default bean is strict):
  - refused before any connection, outcome `BLOCKED_ADDRESS`: loopback (127.0.0.0/8, `::1`), any-local (`0.0.0.0`,
    `::`), 10/8, 172.16/12, 192.168/16, link-local 169.254/16 incl. `169.254.169.254`, `fe80::/10`, 100.64/10,
    `fc00::/7`, multicast, IPv4-mapped IPv6 forms of these; a host resolving to several addresses of which one is
    private; non-http(s) scheme; port other than 80/443 (PO-8);
  - redirects followed by hand, each hop re-checked (redirect to a private address → `BLOCKED_ADDRESS`), relative
    `Location` resolved, at most 5 hops (PO-7), the final address returned (data-model.md `Website page.finalUrl`);
  - body read as a stream and cut at 1 MiB, cut then parsed, not rejected (PO-1); non-HTML content type →
    `NOT_HTML`; connection refused, error or non-2xx → `UNREACHABLE` (T26);
  - request headers: `Accept: text/html`, `User-Agent` from `card-enrichment.user-agent` (PO-6); `robots.txt` never
    requested (PO-6); each request's timeout is the remaining budget (research R6);
  - `isImage(url)`: same checks, accepted only on HTTP 200 with `Content-Type` starting with `image/`; the body is
    not decoded (research R4).
- [X] T009 Create `enrichment/SafeWebFetcher.java` (`RestClient` from the `AppConfig` builder on the JDK
  `HttpClient`, redirects disabled, manual hops, size cut through `exchange()`, injectable address check) and
  `enrichment/EnrichmentOutcome.java` (data-model.md "Enrichment outcome": `SHARING_IMAGE`, `MODEL_CHOICE`,
  `NO_IMAGE`, `MODEL_DECLINED`, `MODEL_INVALID`, `MODEL_UNAVAILABLE`, `UNREACHABLE`, `BLOCKED_ADDRESS`, `NOT_HTML`,
  `TIMEOUT`). Makes T008 pass. *(Outcome list changed by T048.)*

**Checkpoint**: foundation ready; US1 backend can start.

---

## Phase 3: User Story 1 (backend part) - a photo taken from the website (P1) 🎯 MVP

**Goal**: `GET /api/card-enrichment?website=` answers within 5 s the declared sharing image, or the image chosen by
the model among the page images, or no photo; never an error for an enrichment failure.

**Independent Test**: `mvn -f backend test`; curl checks of quickstart.md section 2.

### Tests for User Story 1 backend (write first, must fail)

- [X] T010 [P] [US1] Add recorded HTML pages under `backend/src/test/resources/enrichment-pages/` (invented content,
  marked NOT real data in a comment): absolute `og:image`; relative `og:image` with and without `<base href>`;
  `og:image:secure_url` only; `og:image:url` only; `twitter:image` only; `<link rel="image_src">` only; no sharing
  image with several `<img>` (`.svg` logo, `.ico`, an image declared 50 px wide, a dish, the room, a `data:` image,
  `data-src`, `data-lazy-src`, `srcset` with several entries, a duplicate address, more than 20 usable images);
  a page with no `<img>`; a page over 1 MiB with `og:image` in `<head>`.
- [X] T011 [P] [US1] Extend `google/GooglePlaceMapperTest.java` (T1, T2, T3, TC-3): valid `websiteUri` →
  data-model.md `Restaurant.website` set (T1); absent → absent (T2); `ftp://…`, `monresto`, user-info, port 8080 →
  `Restaurant.website` absent (T3).
- [X] T012 [P] [US1] Write `enrichment/WebsitePageReaderTest.java` with the T010 pages (T5, T6, T11, PO-2, PO-3,
  PO-5, FR-006), citing data-model.md `Website page.finalUrl`, `Website page.title`, `Website page.sharingImage`,
  `Website page.candidates`, `Image candidate.number`, `Image candidate.url`, `Image candidate.alt`,
  `Image candidate.width`, `Image candidate.height`:
  sharing image = first non-blank of `og:image`, `og:image:secure_url`, `og:image:url`, then `twitter:image`, then
  `<link rel="image_src">` (PO-5); resolved against the final page address and `<base href>` (T6); candidates read
  from `src`, `data-src`, `data-lazy-src` and the largest `srcset` entry (PO-3; how they combine per `<img>`: OQ-3),
  `.svg` and `.ico` dropped, images declared under 100 px wide or high dropped (PO-3), `data:` and non-http(s)
  dropped, deduplicated, document order, ~~at most 20 (PO-2)~~ all of them (T045); page with no image → no candidate (T11); the 1 MiB page
  still gives its `og:image` (PO-1).
- [X] ~~T013~~ **Superseded by T044 (amendment (a))**. [P] [US1] Write `enrichment/ImageChooserTest.java` with a fake `LlmChatClient` (T8, T10, T11, T12, T28,
  FR-006, PO-11), citing data-model.md "Model choice": one `complete(system, user)` call; the system prompt states
  PO-11 (photo of the dining room, the front or a dish; never a logo, icon, map, menu scan, text banner or
  portrait) and asks for `{"choice": <number>}` or `{"choice": null}`; the user message lists numbered candidates
  (number, address, alt, declared width/height) and the page title (restaurant name: OQ-4); answer read through
  `llm/JsonAnswers`; `{"choice": 2}` → candidate 2 (`MODEL_CHOICE`, T8); `null` → `MODEL_DECLINED` (T10); 0, N+1,
  non-integer, an address, no readable object → `MODEL_INVALID` (T12); `SearchException` → `MODEL_UNAVAILABLE`
  (T28); no candidate → the model is not called (T11).
- [X] T014 *(adapted by T046)* [P] [US1] Write `enrichment/CardEnrichmentServiceTest.java` flow (T5, T7, T11, T26, T27, T28, T30,
  SC-002, SC-005), with the local fake website and a fake chooser: sharing image answering `image/*` →
  `SHARING_IMAGE`, model not called (T5); sharing image 404 or not `image/*` → model asked (T7); no sharing image
  and no candidate → `NO_IMAGE`, model not called (T11); connection refused → no photo (T26); model unavailable with
  a sharing image → photo (T27), without → no photo (T28); redirect to a page declaring a sharing image → that
  image (T30); every returned `photoUrl` is the resolved sharing image or one of the page candidates (FR-006,
  SC-005); response per data-model.md `Card enrichment.photoUrl` and `Card enrichment.photoOrigin`; exactly one INFO
  log line per enrichment with outcome, duration and website host, never page content (research R12).
- [X] T015 *(`name` removed by T046/T049)* [P] [US1] Write `api/CardEnrichmentControllerTest.java` (T3, contract, FR-012), following
  `api/PhotoControllerTest.java`: `GET /api/card-enrichment?website=…` → 200 `{photoUrl, photoOrigin}` or 200 `{}`
  (no null fields, no other property: contracts/card-enrichment.openapi.yaml `CardEnrichment`); missing or invalid
  `website` (data-model.md "Card enrichment request" `website`) → 400 without body and the service is not called;
  any enrichment failure → 200 `{}`, never 4xx/5xx.
- [X] T016 *(cases (b) and (c) replaced by T046: T25 is now a slow image check)* [P] [US1] Write `enrichment/CardEnrichmentDeadlineTest.java` (T24, T25, FR-011, PO-4 (a), research R6):
  real `CardEnrichmentService` and real `LlmChatClient` pointed at local fake servers. (a) website answering after
  6 s → 200 `{}` returned before 6 s, outcome `TIMEOUT` (T24). (b) website answering in 1 s with no sharing image
  and images, model answering after 6 s → `{}` before 6 s, outcome `TIMEOUT` (T25). (c) **cancellation proof**: in
  case (b) the fake model server observes its connection closed (or the task thread interrupted) before its 6 s
  answer, and with an executor of size 1 a following enrichment starts at once. The 5 s is measured from the
  request reception (PO-4 (a)); queue wait counts in it (PO-10). If (c) fails because the JDK `HttpClient` does not
  abort on interrupt, stop and report (plan "Risks"); do not add an LLM timeout (out of scope, research R6).

### Implementation for User Story 1 backend

- [X] T017 [US1] Extend `google/GooglePlaceMapper.java`: `websiteUri` kept only when it passes `WebsiteAddress`
  (TC-3). Makes T011 pass.
- [X] T018 [P] [US1] Create `enrichment/WebsitePageReader.java` (jsoup, `absUrl`, rules of T012). Makes T012 pass.
- [X] ~~T019~~ **Superseded by T047 (amendment (a)); `ImageChooser` removed**. [P] [US1] Create `enrichment/ImageChooser.java` (prompt of PO-11, `LlmChatClient` reused unchanged,
  `JsonAnswers`, number → candidate). Makes T013 pass.
- [X] T020 *(flow changed by T048)* [US1] Create `enrichment/CardEnrichment.java` (response record, contract) and
  `enrichment/CardEnrichmentService.java` (flow of data-model.md "State flow", `SearchException` caught, outcome
  log line). Makes T014 pass.
- [X] T021 [US1] Add the 5 s deadline to `enrichment/CardEnrichmentService.java`: bounded executor of
  `card-enrichment.executor-size` (8, PO-10 (2)) declared as a bean, task submitted at request reception, wait for
  the remaining budget of `card-enrichment.budget-seconds`, then `cancel(true)` and `TIMEOUT`; every fetch uses the
  remaining budget as its timeout. Makes T016 pass.
- [X] T022 [US1] Create `api/CardEnrichmentController.java` (`GET /api/card-enrichment`, `WebsiteAddress` check →
  400 without body, else 200 from the service). Makes T015 pass.

---

## Phase 3a: Amendment (a) - largest page image, no language model (backend)

**Goal**: replace the model choice by the fixed rule of spec FR-004; enrichment has no dependency on `LlmChatClient`.

### Tests (written first, failed before T047-T049)

- [X] T044 [P] [US1] Write `enrichment/LargestImageRuleTest.java` (T8, T10, T12, T28, FR-004 steps 2-4,
  data-model.md "Largest image rule"): T8 sizes give the 1200x800 room first, the dish next, `logo.png` excluded;
  "logo" / "icon" in the address or the alt text excluded in any case; no declared size after, in page order (T12);
  equal areas keep page order; the `srcset` width descriptor as the width of the area (OQ-5); a single dimension is
  no declared size (OQ-5); at most 3 returned (T28); nothing left gives an empty list (T10).
- [X] T045 [P] [US1] Adapt `enrichment/WebsitePageReaderTest.java` and fixtures (FR-004 step 1): `number` and
  `title` removed, `srcsetWidth` kept; every image is a candidate (25 of `many-images.html`, no PO-2 limit); new
  fixtures `largest-image.html` (T8) and `only-excluded-images.html` (T10) read then ordered by the rule.
- [X] T046 [P] [US1] Rewrite `enrichment/CardEnrichmentServiceTest.java` with the real reader and rule (no chooser):
  T5 (page images not requested), T7 (broken or non-image sharing image: largest page image), T8, T9 (largest
  broken: second), T10 (no image requested), T11, T12, T28 (4 largest broken, 5th fine: no photo, exactly 3 image
  requests, `NO_DISPLAYABLE_IMAGE`), T26, T30, FR-006. Rewrite `enrichment/CardEnrichmentDeadlineTest.java`: T24
  unchanged; T25 = page in 1 s, largest image after 6 s: `{}` between 4.9 and 6 s, `TIMEOUT`, the next enrichment on
  the single executor thread starts at once. `name` removed from `api/CardEnrichmentControllerTest.java`;
  `SafeWebFetcherTest` outcome list updated.
- [X] T047a [P] [US1] Write `enrichment/EnrichmentWithoutLanguageModelTest.java` (T27): no field nor constructor
  parameter of any class of `search.enrichment` nor of `CardEnrichmentController` has a type of `search.llm`.

### Implementation

- [X] T047 [US1] Create `enrichment/LargestImageRule.java` (steps 2-4, `MAX_CHECKED` = 3 from FR-004); remove
  `enrichment/ImageChooser.java`, `enrichment/ImageChoice.java` and `ImageChooserTest.java` (prompt and model answer
  parsing). Makes T044 pass.
- [X] T048 [US1] `enrichment/CardEnrichmentService.java`: sharing image, else the candidates of the rule checked in
  order with `SafeWebFetcher.isImage`, first displayable gives `LARGEST_PAGE_IMAGE`, none gives
  `NO_DISPLAYABLE_IMAGE`, nothing to check gives `NO_IMAGE`; deadline checked between image checks.
  `enrichment/EnrichmentOutcome.java`: `MODEL_*` replaced by `LARGEST_PAGE_IMAGE` and `NO_DISPLAYABLE_IMAGE`.
  `enrichment/ImageCandidate.java` (`srcsetWidth`, no `number`), `enrichment/WebsitePage.java` (no `title`),
  `enrichment/WebsitePageReader.java` (srcset width kept, no limit). Makes T045, T046, T047a pass.
- [X] T049 [US1] Remove `name` from `api/CardEnrichmentController.java` and `CardEnrichmentService.enrich`; remove
  `max-candidates` from `config/CardEnrichmentProperties.java`, `application.yml`,
  `config/CardEnrichmentPropertiesTest.java` and `config/TestCardEnrichmentProperties.java`.
- [X] T050 Update plan.md ("Amendment (a)"), research.md (R7 superseded, R13), data-model.md, the contract (no
  `name`; `photoOrigin` `SHARING_IMAGE` or `LARGEST_PAGE_IMAGE`) and quickstart.md. `mvn -f backend test` green.

**Checkpoint**: backend enrichment works with no language model.

---

## Phase 3b: Amendment (b) - Unsplash cuisine photo as the last resort (backend, then frontend)

**Goal**: when the page was read and neither the sharing image nor a page image is displayable, the card gets a
free Unsplash photo of its cuisine with its credit (spec US1 AS7-AS8, FR-006, FR-012 to FR-016, T31-T43; plan.md
"Amendment (b) (Unsplash)", research R14). Values: spec FR-013 (15 results, lang fr, hash position), FR-015 (app
name `reco_resto`), FR-016 (1 h), plan table "Configuration".

### Tests (written first, must fail)

- [X] T051 [P] [US1] Extend `config/CardEnrichmentPropertiesTest.java`: `card-enrichment.unsplash.*` defaults
  (`base-url` `https://api.unsplash.com`, `access-key` blank, `app-name` `reco_resto`, `per-page` 15, `cache-ttl`
  1 h) and overrides (`UNSPLASH_ACCESS_KEY`, `CARD_ENRICHMENT_UNSPLASH_*`); the key never appears in `toString`
  (FR-014).
- [X] T052 [P] [US1] Write `enrichment/UnsplashClientTest.java` with the local stub (`FakeWebsite`, query string now
  recorded): `GET /search/photos?query=Restaurant italien&per_page=15&lang=fr`, `Authorization: Client-ID <key>`,
  `Accept-Version: v1`, key in no URL (T31, T39); answer read into photos (`urls.regular`, `user.name`,
  `user.links.html`), non-https or unparseable addresses and blank names skipped, at most 15; 429, 500, unreachable,
  unreadable JSON give `UNSPLASH_UNAVAILABLE` with one WARN line carrying the status or cause class and never the
  key (T34, T39); a stub slower than the remaining budget gives `TIMEOUT` within the budget; no key: not enabled
  (T36).
- [X] T053 [P] [US1] Write `enrichment/CuisinePhotosTest.java` (injected clock): T37 (same 15 photos, two websites
  get the photos at `floorMod(website.hashCode(), 15)`, the same website always the same photo); T38 (blank, absent
  or spaces-only cuisine gives the query "restaurant"); T42 (10 min apart: one request), T43 (61 min apart: two
  requests); query normalized for the cache (case, inner blanks); a failure is not cached; no usable photo gives
  nothing (T33); credit with `utm_source=reco_resto&utm_medium=referral` on the photographer profile and on
  `https://unsplash.com/` (FR-015); concurrent enrichments of one cuisine share one request (FR-016).
- [X] T054 [P] [US1] Extend `enrichment/CardEnrichmentServiceTest.java`: T31 (no usable page image: Unsplash photo,
  origin `UNSPLASH_CUISINE_PHOTO`, credit, query "Restaurant italien"); after `NO_DISPLAYABLE_IMAGE` too; T33 (no
  result: `NO_CUISINE_PHOTO`, `{}`); T34 (429: `UNSPLASH_UNAVAILABLE`, `{}`); T35 (sharing image: no Unsplash
  request); T36 (no key: no request, website outcome kept); T26 / OQ-6 (unreachable website: no Unsplash request).
  Extend `enrichment/CardEnrichmentDeadlineTest.java`: Unsplash slower than the budget gives `{}` before 6 s,
  `TIMEOUT` (FR-011).
- [X] T055 [P] [US1] Extend `api/CardEnrichmentControllerTest.java`: `cuisine` forwarded to the service (absent:
  null); the `credit` object in the response exactly as the contract; no key in any response (T39).

### Implementation

- [X] T056 [US1] `config/CardEnrichmentProperties.java`: nested `Unsplash` record (key masked in `toString`);
  `application.yml` `card-enrichment.unsplash` section with one comment per value. Makes T051 pass.
- [X] T057 [US1] Create `enrichment/UnsplashClient.java` and `enrichment/UnsplashPhoto.java` (fixed API host, not
  `SafeWebFetcher`; remaining budget as read timeout; `lang=fr` constant). Makes T052 pass.
- [X] T058 [US1] Create `enrichment/CuisinePhotos.java` (query, cache with `Clock`, one request in flight per query,
  hash position, credit) and `enrichment/PhotoCredit.java`. Makes T053 pass.
- [X] T059 [US1] `enrichment/EnrichmentOutcome.java` (`UNSPLASH_CUISINE_PHOTO`, `NO_CUISINE_PHOTO`,
  `UNSPLASH_UNAVAILABLE`), `enrichment/CardEnrichment.java` (`credit`), `enrichment/CardEnrichmentService.java`
  (step 6 of plan "Amendment (b)"), `api/CardEnrichmentController.java` (`cuisine`). Makes T054, T055 pass.
- [X] T060 Docs: plan.md, research.md (R14), data-model.md, contract, quickstart.md, `docs/deployment.md`
  (`UNSPLASH_ACCESS_KEY`, outbound HTTPS to `api.unsplash.com`, browser loads `images.unsplash.com`).
  `mvn -f backend test` green.

### Frontend (open; after Phase 6, shared files sequenced as in "Shared files")

- [X] T061 [US1] Test first in `frontend/src/app/enrichment/card-enrichment.store.spec.ts`, then
  `card-enrichment.store.ts` and `card-enrichment.ts` (done together with T025/T029): the request carries `cuisine=<Restaurant.cuisine>` (encoded);
  the store keeps the `credit` of the answer next to the photo (contract `PhotoCredit`). The store key stays the
  website (PO-13).
- [X] T062 [US1] Test first in `frontend/src/app/deck/swipe-card/swipe-card.component.spec.ts`, then the component
  and `deck.texts.ts`: with a credit, "Photo : <photographerName> / Unsplash", the name linking to
  `photographerUrl`, "Unsplash" to `unsplashUrl`, both `target="_blank" rel="noopener noreferrer"`
  `draggable="false"`, `pointerdown` stops propagation, a click emits no reaction (T40); no credit for a website photo
  (T41). Placement next to the 008 "Google Maps" attribution and the link icon: to be validated by the product owner.
  *Done: credit at the top-right of the photo area (link icon top-left, distance bottom-left, "Google Maps"
  bottom-right), same discreet look as the Google attribution; drag avoided by the existing 008 rule of
  `onPointerDown` (a press on any `<a>` starts no drag nor pointer capture) instead of `stopPropagation`.*
- [ ] T063 Manual check with the real key (quickstart.md section 2 and section 3 step 7), outcome counts recorded in
  the PR; the demo limit (50 requests per hour) is checked in the Unsplash dashboard after the run.

**Checkpoint**: backend gives the Unsplash cuisine photo with its credit; frontend open.

---

## Phase 4: Backend validation checkpoint

**Purpose**: the endpoint is accepted before any frontend change; can be done before 008 frontend is finished.

- [ ] T023 Run `mvn -f backend test` (all green), then quickstart.md section 2 "Endpoint checks" with
  `mvn -f backend spring-boot:run`: a real site with `og:image` → `SHARING_IMAGE` within 5 s; `ftp://example.com` →
  400 (T3); `http://169.254.169.254/` and `http://localhost:8080/` → `{}` with `BLOCKED_ADDRESS` logged (SSRF);
  `http://10.255.255.1/` → `{}` in at most 5 s (T24/T26). Record the outcomes in the PR.

**Checkpoint**: backend accepted.

---

## Phase 5: Frontend gate (blocking all frontend tasks)

- [X] T024 Confirm 008 T062 (`website` in `frontend/src/app/restaurant/restaurant.ts`,
  `frontend/src/app/search/search-result.ts`, `frontend/src/app/search/deck-input.ts`), T063 (`<img>` error →
  tablecloth in `frontend/src/app/deck/swipe-card/swipe-card.component.*`) and T065 ("Google Maps" attribution) are
  done, and this branch is rebased on them; `npm --prefix frontend test -- --watch=false` green. Do not
  re-implement any of them; if one is missing, stop.
  *Done 2026-11-26: the 008 frontend tasks T060-T066 were copied uncommitted from the 008 branch into this worktree
  instead of a rebase (008 not merged yet); frontend suite green (212 tests) before the 007 frontend work.*

---

## Phase 6: User Story 1 (frontend part) - the card shows the website photo (P1)

**Goal**: after the deck is shown, each card of a restaurant with a website and no photo gets the website photo once
the browser has loaded it; otherwise the tablecloth, no error.

**Independent Test**: `npm --prefix frontend test -- --watch=false`; quickstart.md section 3 steps 1-2.

### Tests for User Story 1 frontend (write first, must fail)

- [X] T025 [P] [US1] Write `frontend/src/app/enrichment/card-enrichment.store.spec.ts` (T4, T9, T26, T29, FR-002,
  FR-012, PO-4, PO-10 (1), PO-13), citing data-model.md "Card enrichment state" and the restaurant fields
  `Restaurant.website`, `Restaurant.photoUrl`: only restaurants with `website` and no `photoUrl` are requested
  (T4); `GET /api/card-enrichment?website=<encoded>`; one request per distinct website (chains share it); at most 3
  requests at the same time, others `queued`, current card first then deck order; 200 with `photoUrl` → preload
  with an `Image` → `load` → `photo(url)`; preload `error` → `none` (T9); 200 `{}`, non-200, network error → `none`
  (T26, T29); no answer within 7 s → `none` (PO-4 (a), fake timers); results kept for the whole session keyed by
  website: same website in a later search → no new request (PO-13); the store never writes `DeckStore`.
- [X] T026 [P] [US1] Extend `frontend/src/app/deck/swipe-card/swipe-card.component.spec.ts` photo part (T5, T8, T9,
  T10, PO-12): an `enrichment` input (`EnrichedPhoto`: url and optional credit; named `photoUrl` before amendment (b)) given → `<img>` with that address and `referrerpolicy="no-referrer"`; no input →
  tablecloth; the 008 T063 error fallback still holds.
- [X] T027 [P] [US1] Extend `frontend/src/app/deck/deck.component.spec.ts` wiring part (T4, T5): at deck start the
  store is asked for the deck cards (current card, then the planned cards, then the cards already shown; not the
  candidates outside the deck, which are never shown); the card receives the effective photo of data-model.md ("Effective
  photo": `restaurant.photoUrl ?? photo(website)`); a data-source photo always wins (T4).

### Implementation for User Story 1 frontend

- [X] T028 [P] [US1] Create `frontend/src/app/enrichment/card-enrichment.ts` (response type of
  contracts/card-enrichment.openapi.yaml `CardEnrichment`).
- [X] T029 [US1] Create `frontend/src/app/enrichment/card-enrichment.store.ts` (root signal service, `HttpClient`,
  constants next to it: concurrency 3 (PO-10 (1)), safety timeout 7 s (PO-4 (a))). Makes T025 pass.
- [X] T030 [US1] Add the `enrichment` input (effective photo rule of data-model.md computed in the card, so the
  data-source photo wins in one place) and `referrerpolicy="no-referrer"` in
  `frontend/src/app/deck/swipe-card/swipe-card.component.ts` and `.html` (shared file, after 008 T063/T065).
  Makes T026 pass.
- [X] T031 [US1] Wire the store in `frontend/src/app/deck/deck.component.ts` and `.html` (request at deck start,
  effective photo binding). Makes T027 pass.

**Checkpoint US1**: cards with a website show their photo; the others behave as in 003.

---

## Phase 7: User Story 3 - enrichment never slows the search or the deck (P1)

**Goal**: the deck opens without waiting; each card updates in place, independently, without touching position or
reactions.

**Independent Test**: deck component and swipe-card specs; quickstart.md section 3 steps 2-4; SC-001 measurement.

### Tests for User Story 3 (write first, must fail)

- [X] T032 [P] [US3] Extend `frontend/src/app/deck/deck.component.spec.ts` (T17, T18, T19, T20, T22, T23, T29,
  SC-003): the deck and its first card render while every enrichment request is still pending (T17, T18); an answer
  for the current card shows the photo in place, same current card, same position, same reactions, `DeckState`
  object unchanged (T19); after a like on card 2, its answer keeps the like and the photo shows on rewind (T20,
  T23); a restaurant brought by refinement (`refinement.service.ts`) is requested when first seen (T22); card 2
  failing while 1 and 3 succeed (T29).
- [X] T033 [P] [US3] Extend `frontend/src/app/deck/swipe-card/swipe-card.component.spec.ts` (T21): changing the
  `photoUrl` input during a drag keeps the drag signals (`dx`, `dy`, `start`) and the card position.

### Implementation for User Story 3

- [X] T034 [US3] In `frontend/src/app/deck/deck.component.ts`, request the deck cards newly brought by refinement
  (an `effect` on the deck state; the store ignores websites already known); fix whatever T032/T033 reveal without changing `deck-state.ts` or `deck.store.ts`
  (research R9). Makes T032 and T033 pass.

**Checkpoint US3**: SC-003 holds in tests.

---

## Phase 8: User Story 2 - open the restaurant website (P2)

**Goal**: a link icon on the card front opens the website in a new tab, without reaction or drag.

**Independent Test**: swipe-card and deck specs; quickstart.md section 3 steps 5-6.

### Tests for User Story 2 (write first, must fail)

- [X] T035 [P] [US2] Extend `frontend/src/app/deck/swipe-card/swipe-card.component.spec.ts` (T13, T14, T16, FR-007,
  PO-14): with `website`, an `<a href=website target="_blank" rel="noopener noreferrer" draggable="false">` with an
  inline SVG link icon and accessible name "Site du restaurant" from `DECK_TEXTS`, at the top-left of the photo
  area, not overlapping the 008 "Google Maps" attribution (T13); none without `website` (T14); `pointerdown` on the
  link does not start a drag nor capture the pointer, the card does not move, no swipe (T16); a click emits no
  reaction.
- [X] T036 [P] [US2] Extend `frontend/src/app/deck/deck.component.spec.ts` (T15, FR-008): deck at card 3 with 2
  reactions, click on the link → same card 3, 2 reactions, none added.

### Implementation for User Story 2

- [X] T037 [P] [US2] Add the accessible name "Site du restaurant" (PO-14) to `frontend/src/app/deck/deck.texts.ts`.
- [X] T038 [US2] Add the link to `frontend/src/app/deck/swipe-card/swipe-card.component.html`, `.ts` and `.css`
  (`pointerdown` on a link is ignored by the card: existing 008 rule reused, no pointer capture, no drag). Makes T035 and T036 pass.

---

## Phase 9: Polish & cross-cutting

- [ ] T039 [P] Update `docs/deployment.md`: the `card-enrichment` settings and their optional `CARD_ENRICHMENT_*`
  overrides (no secret, defaults in `application.yml`); the backend now makes outbound HTTP/HTTPS requests to
  restaurant websites (ports 80/443, PO-8); in "Known limits": no abuse protection on `/api/card-enrichment` for
  the POC, a per-IP limit is required before any public announcement (PO-9); DNS rebinding residual risk (research
  R5); images are loaded by the user's browser from the restaurant site (PO-12) and `http` images may fail once the
  app is served over HTTPS (research R8).
- [ ] T040 [P] Security review (research R5, R12): replay the SSRF curls of T023 against the running stack; grep the
  backend logs for page content and the LLM key (0 occurrences); confirm no backend cache or file is written
  (PO-13). Record in the PR.
- [ ] T041 Acceptance run by the product owner, quickstart.md section 3 (T13-T16, T19-T23, SC-002, SC-003).
- [ ] T042 Measurements, quickstart.md section 4, on "Je veux manger à opera ce midi", 3 runs each: SC-001 / T17
  (time to the first card, enrichment requests blocked vs not, means equal within noise); SC-004 (share of deck
  cards with a website showing a real photo, target ≥ 50 %, with the backend outcome counts); SC-005 (each
  `photoUrl` present in the page source after redirects). If SC-004 is missed, report the outcome counts to the
  product owner; do not change PO values.
- [ ] T043 Final verification: `mvn -f backend test`, `npm --prefix frontend test -- --watch=false` and
  `npm --prefix frontend run build` green; every row of "Spec test coverage" below checked; PR description lists
  the DRY report (reused / extended / created), deviations, the T25 cancellation result and the open questions.

---

## Dependencies & Execution Order

### Phase dependencies

- Backend: Phase 1 → Phase 2 → Phase 3 (US1 backend) → Phase 3a (amendment (a)) → Phase 3b backend (T051-T060,
  amendment (b)) → Phase 4. Frontend T061-T062 after Phase 6 (T029-T031). Can start immediately, in parallel with the 008
  frontend work.
- Frontend: Phase 5 (gate: 008 T062, T063, T065 done and rebase) → Phase 6 (US1 frontend) → Phase 7 (US3) →
  Phase 8 (US2). Phase 6 also needs Phase 4 for end-to-end checks (unit tests use `HttpTestingController`).
- Phase 9 after all; T039 can be written any time after T003.
- T001 and T003 were blocked by OQ-1 and OQ-2, T012/T018 by OQ-3 (all answered). OQ-4 is obsolete. OQ-5 is answered
  (reading (a), as implemented).

### Shared files (sequence, not parallel)

- `frontend/src/app/deck/swipe-card/swipe-card.component.*`: 008 T063 → 008 T065 → T030 → T033/T034 → T038.
- `frontend/src/app/deck/deck.component.*` and `deck.component.spec.ts`: T027 → T031 → T032 → T034 → T036.
- `enrichment/CardEnrichmentService.java`: T020 → T021 → T048 → T059.
- `frontend/src/app/enrichment/card-enrichment.store.*`: T025 → T029 → T061; `swipe-card.component.*`: ... → T038 → T062. `backend/src/main/resources/application.yml`: T003 → T049.
- `google/GooglePlaceMapper.java`: T017 only (after 008 T038, already done).

### Critical path

T002 → T003 → T008 → T009 → T014 → T020 → T016 → T021 → T015 → T022 → T023 → (008 T062/T063/T065) → T024 → T025 →
T029 → T030 → T031 → T032 → T034 → T035 → T038 → T042 → T043.

## Parallel examples

```text
Phase 1: T001, T002 together.
Phase 2: T004, T006, T008 together; then T005, T007, T009.
US1 backend tests: T010, T011, T012, T013, T014, T015, T016 together; then T017, T018, T019 in parallel.
US1 frontend: T025, T026, T027, T028 together.
US3: T032, T033 together.
US2: T035, T036, T037 together.
Polish: T039, T040 together.
```

## Implementation strategy

1. MVP = Phases 1-4: the endpoint works and is safe (SSRF, 5 s), validated with curl, while 008 finishes its
   frontend.
2. After the gate: US1 frontend (photos on cards), then US3 (in-place update proven), then US2 (link).
3. Polish: docs, security review, acceptance and measurements (SC-001, SC-004, SC-005).

## Spec test coverage

| Spec tests | Tasks |
|---|---|
| T1 | T011, T017 |
| T2 | T011, T017, T035 (T14 no link) |
| T3 | T004, T005, T011, T015, T023 |
| T4 | T025, T027 |
| T5 | T012, T014, T046, T026, T027 |
| T6 | T012 |
| T7 | T014, T046 |
| T8 | T044, T045, T046, T026 |
| T9 | T046, T025, T026 |
| T10 | T044, T045, T046, T026 |
| T11 | T012, T046 |
| T12 | T044, T046 |
| T13 | T035, T041 |
| T14 | T035, T041 |
| T15 | T036, T041 |
| T16 | T035, T041 |
| T17 | T032, T042 |
| T18 | T032 |
| T19 | T032, T041 |
| T20 | T032, T041 |
| T21 | T033, T041 |
| T22 | T032 |
| T23 | T032, T041 |
| T24 | T016, T046, T023 |
| T25 | T046 (slow image check; executor freed) |
| T26 | T008, T014, T046, T025, T023 |
| T27 | T047a |
| T28 | T044, T046 |
| T29 | T025, T032 |
| T30 | T008, T014, T046 |
| SC-001 | T032, T042 |
| SC-002 | T014, T015, T025, T041 |
| SC-003 | T032, T033, T041 |
| SC-004 | T042 |
| SC-005 | T014, T046, T042 |
| SSRF (research R5) | T008, T023, T040 |
| T31 | T052, T054, T059, T063 |
| T32 | T015 (400 without website), T025 (no request without website) |
| T33 | T053, T054 |
| T34 | T052, T054 |
| T35 | T054 |
| T36 | T051, T052, T054 |
| T37 | T053 |
| T38 | T053 |
| T39 | T051, T052, T055 |
| T40 | T062, T063 |
| T41 | T062 |
| T42 | T053 |
| T43 | T053 |

## Notes

- [P] = different files, no dependency on an incomplete task. Shared files are sequenced above.
- Every test task must fail before its implementation task; commit only when asked.
- plan.md says Karma/Jasmine for the frontend, but `frontend/package.json` lists `vitest` with `ng test`: use the
  existing runner as configured, do not add one.
- If a needed value is not in plan.md "Product owner decisions (2026-11-26)", the spec or research.md, stop and ask
  (constitution III).

## Open questions

- **OQ-1** (TC-1): version of `org.jsoup:jsoup` to add to `backend/pom.xml`. Not given in plan.md or research.md.
  Blocks T001.
- **OQ-2** (PO-6): exact `User-Agent` value ("honest name naming the project with its URL": which name, version and
  URL). Blocks the `user-agent` value of T002/T003.
- **OQ-3** (PO-3): for one `<img>` carrying several of `src`, `data-src`, `data-lazy-src`, `srcset`, is each
  distinct address a separate candidate (then deduplicated), or is one address kept per `<img>` (and with which
  precedence)? Blocks the candidate part of T012/T018.
- **OQ-4** (obsolete, amendment (a): `name` removed) (research R7 vs contract): research R7 sends "the restaurant name and the page title" to the model, but
  the approved contract has only the `website` parameter, so the backend does not know the name. Keep the contract
  (page title only), or add a `name` query parameter to `GET /api/card-enrichment`? Blocks the prompt part of
  T013/T019.

- **OQ-5** (answered, see below) (amendment (a), FR-004 step 3): "declared area (width x height; for a `srcset` entry, its width
  descriptor)". Implemented reading (research R13): the width descriptor replaces the `width` attribute and is
  multiplied by the declared `height`; a candidate with only a width (attribute or descriptor) or only a height has
  no declared area and comes after, in page order. Other readings: the descriptor alone is the sort value (a `1200w`
  image would then rank below a 300x200 one), or a descriptor without height still counts as declared. To be
  confirmed by the product owner; only `LargestImageRule.area` and its tests change if another reading is chosen.

### Answers (product owner, 2026-11-26)

- OQ-1: jsoup `1.24.1`. OQ-3: one candidate per `<img>` (largest `srcset` entry, else `data-src`, else `data-lazy-src`,
  else `src`). OQ-5: reading (a), as implemented (product owner, 2026-11-26): the `srcset` width descriptor replaces the
  `width` attribute and is multiplied by the declared `height`; an image with only one of the two has no declared
  area and goes after, in page order (clarification added to spec FR-004 step 3). ~~OQ-4: add an optional `name` query parameter to `contracts/card-enrichment.openapi.yaml` (used only in
  the model prompt); the frontend sends the restaurant name.~~ Obsolete (amendment (a)): `name` removed. OQ-2: User-Agent `RecoResto/0.1 (proof of concept)`. Details in plan.md.

### Open questions of amendment (b) (for the product owner; details in plan.md "Amendment (b) (Unsplash)")

- **OQ-6**: cuisine photo also for an unreadable website (`UNREACHABLE`, `BLOCKED_ADDRESS`, `NOT_HTML`)? Implemented:
  no (T26 Expected kept).
- **OQ-7**: Expected of T10, T11, T28 (tablecloth) to restate now that a key gives an Unsplash photo in these cases.
- **OQ-8**: `lang` is a beta Unsplash parameter enabled on request; sent anyway (FR-013).
- **OQ-9**: no download tracking request (`links.download_location`): a card display is not a download.
- **OQ-10**: `UNSPLASH_ACCESS_KEY` to add to `.github/workflows/deploy.yml` and the GitHub secrets (not done).
