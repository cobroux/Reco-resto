# Feature Specification: Card Website Enrichment

**Feature Branch**: `feat/card-website-enrichment` (based on `feat/google-places-source`, spec 008)

**Created**: 2026-11-25 — **Updated**: 2026-11-26 (Google Places source, photo only; amendment (a): no language model; amendment (b): Unsplash cuisine photo as last resort)

**Status**: Approved (product owner, 2026-11-26, including SC-004 50 %, redirects followed, home page only, no loading indicator). Amendment (a) of 2026-11-26 (no language model, largest page image) decided by the product owner; its new rule values were confirmed by the product owner on 2026-11-26.

**Input**: User description: "Use the restaurant's website to give its card a photo. If an image is readily available from the website (the image the page declares for sharing), use it; otherwise ask the language model to choose an image from the website [replaced by amendment (a): the largest image of the page]; if none, keep the existing tablecloth fallback. Show a link to the restaurant website on the card. Enrichment is loaded card by card, after the deck is shown; it must not delay the search." Decided by the product owner for the proof of concept; image rights accepted by the team.

**Why now (2026-11-26)**: with spec 008 the restaurants come from Google Places, but Google returns no photo at all for this project (field `photos` absent on every place in local runs, probably the EEA terms of Google Maps Platform). The website address (`websiteUri`) is returned for most places, so the restaurant's own website is the photo source.

**Feature set**: this spec extends features 002 natural-language search (FR-012: photo "when available"), 003 swipe deck (US1 AS2–AS3: card front, tablecloth fallback) and 008 Google Places source (website address already in the response). It does not change 004 restaurant card details, 005 results map screen or 006 route to restaurant.

**Amendment (a) (product owner, 2026-11-26)**: the language model is no longer used for enrichment. The LLM provider limits the key to 1,500 tokens per minute, and one model call per card (about 1,200 tokens) would exhaust it and block the search itself. When the website declares no usable sharing image, the largest image of the page is used, chosen by a fixed rule. Image search APIs (option (b)) are not used. [Superseded by amendment (b).]

**Amendment (b) (product owner, 2026-11-26)**: when neither the sharing image nor a page image gives a displayable photo, the card shows a free stock photo matching the restaurant cuisine, taken from the Unsplash API (free key, no payment, no language model, no token; it replaces Pexels, whose key issuance is paused). It is NOT a photo of the restaurant itself: it illustrates its cuisine. Only restaurants that have a website are concerned. Paid image search APIs (Brave) and Google Street View are not used. The Unsplash photo is credited on the card as the Unsplash guidelines ask (photographer and Unsplash).

**Product owner decisions (2026-11-26)**: only the photo is taken from the website (the "À goûter" dish and the other card fields are out of scope); enrichment of a card is abandoned after 5 s; the Expected column of this spec is written by Claude and reviewed by the product owner (same exception as spec 008); the feature is built on top of spec 008.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See a real photo of the restaurant (Priority: P1)

Philipe swipes through the deck. Today every card shows the default tablecloth because the data source gives no photo. With this feature, when the restaurant has a website, its card shows a picture taken from that website, so he can judge the place at a glance.

**Why this priority**: the photo is the largest element of the card front and is always the fallback today; it is the most visible gain.

**Independent Test**: feed the deck with restaurants whose websites are controlled test pages (one declaring a sharing image, one with images but none declared, one with no image, one unreachable, one restaurant with no website) and check the photo shown on each card.

**Acceptance Scenarios**:

1. **Given** a restaurant has no photo from the data source and has a website that declares an image for sharing, **When** its enrichment is done, **Then** its card shows that image as the photo.
2. **Given** a restaurant has no photo from the data source and has a website that declares no image for sharing but contains images, **When** its enrichment is done, **Then** its card shows the largest image of the page, chosen by the rule of FR-004.
3. **Given** the page has no image left after the rule of FR-004 (none, or only excluded ones), **When** its enrichment is done, **Then** the card keeps the default tablecloth visual (feature 003, US1 AS3).
4. **Given** a restaurant has no website, **Then** no enrichment is attempted and the card behaves exactly as in feature 003.
5. **Given** the website cannot be reached or the enrichment is not done within 5 s, **Then** the card keeps the tablecloth and no error is shown to the user.
6. **Given** a restaurant already has a photo from the data source, **Then** the photo from the data source is kept: the website is only used when no photo is known (product owner, 2026-11-25).
7. **Given** neither the sharing image nor a page image gives a displayable photo, **When** its enrichment is done, **Then** its card shows a Unsplash photo matching the restaurant cuisine, with its credit (FR-013, FR-015); if Unsplash gives none, the card keeps the tablecloth.
8. **Given** Unsplash refuses the request (limit reached, no key, error), **Then** the card keeps the tablecloth, with no error shown.

---

### User Story 2 - Open the restaurant website (Priority: P2)

Philipe wants to check the restaurant by himself; the card offers a link to its website.

**Why this priority**: useful to decide, but the deck already works without it.

**Independent Test**: on cards of restaurants with and without a website, check the link presence, then follow it and check the deck is still at the same card with reactions kept.

**Acceptance Scenarios**:

1. **Given** a restaurant has a website, **Then** its card shows a link to it, placed on the card front, shown as a link icon (product owner, 2026-11-25).
2. **Given** a restaurant has no website, **Then** no website link is shown.
3. **Given** the user follows the website link, **Then** the website opens in a new browser tab: back in the application, the same card is shown and all reactions are kept.
4. **Given** the user follows the website link, **Then** this does not count as a reaction (like, dislike or pick) and does not start a drag of the card.

---

### User Story 3 - Enrichment never slows down the search or the deck (Priority: P1)

Philipe gets his deck as fast as without this feature; photos arrive later, card by card.

**Why this priority**: the search target is under 20 s (issue A, spec 008); enrichment must not add to it.

**Independent Test**: run the same search with enrichment enabled and with websites made very slow, and check the deck opens at the same time as without enrichment, then cards get their photo as their enrichment arrives.

**Acceptance Scenarios**:

1. **Given** a search returns restaurants with websites, **When** the deck opens, **Then** it opens without waiting for any enrichment: the time to the first card does not depend on websites.
2. **Given** the deck is shown, **Then** each restaurant is enriched separately, and a card is updated as soon as its own enrichment is done, independently of the other cards.
3. **Given** a card is already shown, **When** its enrichment arrives, **Then** the card shows the photo in place, without changing the current card, the deck position or the reactions.
4. **Given** the user already reacted to a card, **When** its enrichment arrives, **Then** the reaction is kept unchanged.
5. **Given** the enrichment of a card fails, **Then** the other cards are still enriched and the deck goes on normally.

---

### Edge Cases

- The website address is malformed or is not an `http`/`https` address: no enrichment is attempted, and no link is shown.
- The website redirects to another address (booking platform, social network page): the redirect is followed and the final page is treated as the restaurant website.
- The website declares a sharing image that cannot be displayed (broken, not an image): it is treated as no declared image, and the largest page image is used (US1 AS2).
- The chosen page image, or the sharing image, fails to load in the browser: the card shows the tablecloth.
- Refinement (feature 003, US3) brings new restaurants into the deck: they are enriched in the same way, card by card.
- The user rewinds to a card (feature 003, US4): it shows its photo if its enrichment is already done.
- Several restaurants share the same website (chain): each card is enriched from that website.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide, for each restaurant, its website address when the data source (Google Places, spec 008) has one, and MUST NOT provide one otherwise.
- **FR-002**: The system MUST NOT attempt any enrichment for a restaurant with no valid `http`/`https` website address, nor for a restaurant that already has a photo; its card MUST behave exactly as in feature 003.
- **FR-003**: When the website declares an image for sharing and that image can be displayed, the system MUST use it as the card photo.
- **FR-004**: When the website declares no usable sharing image, the system MUST use the largest image of the page, chosen by this rule, and MUST NOT call any language model nor any image search service:
  1. Candidates: the `<img>` of the page, one address per image (largest `srcset` entry, else `data-src`, else `data-lazy-src`, else `src`); `.svg` and `.ico` images and images declared under 100 px wide or high are excluded (plan PO-3, OQ-3).
  2. Images whose address or alternative text contains "logo" or "icon" (any case) are excluded. *(confirmed by the product owner, 2026-11-26)*
  3. The remaining candidates are ordered by declared area (width x height; for a `srcset` entry, its width descriptor) (the srcset width replaces the width attribute; area = that width x declared height), largest first; images with no declared size come after, in page order. *(confirmed by the product owner, 2026-11-26)*
  4. The candidates are checked in that order and the first one that can be displayed is used; at most 3 candidates are checked. *(confirmed by the product owner, 2026-11-26)*
- **FR-005**: When no image is obtained (FR-003 and FR-004), the card MUST keep the default tablecloth visual (feature 003, US1 AS3).
- **FR-006**: Every image shown from enrichment MUST be an image referenced by the restaurant's own website page (plan PO-15: whatever its host), or a Unsplash cuisine photo of FR-013 (shown with its credit).
- **FR-007**: A card MUST show a link to the restaurant website when it has one (on the card front, as a link icon, opening in a new browser tab), and MUST NOT show one otherwise.
- **FR-008**: Following the website link MUST NOT count as a reaction, MUST NOT start a drag and MUST NOT lose the deck position or the reactions.
- **FR-009**: The search (feature 002) and the opening of the deck (feature 003) MUST NOT wait for any enrichment.
- **FR-010**: Enrichment MUST be loaded per restaurant, after the deck is shown; a card MUST be updated in place as soon as its own enrichment is done, without changing the current card, the deck position or the reactions.
- **FR-011**: The enrichment of one restaurant MUST be abandoned when it is not done within 5 s (page and image checks together); the card then keeps the tablecloth.
- **FR-013**: When FR-003 and FR-004 give no displayable photo, the system MUST send one search to the Unsplash API with the restaurant cuisine as shown on the card (for example "Restaurant italien"; "restaurant" when the cuisine is unknown), in French (`lang` fr), and MUST use one photo of the answer, chosen so that two restaurants of the same cuisine do not all get the same photo: among the first 15 results, the one at the position given by the restaurant website address (a stable hash modulo the number of results). The search counts in the 5 s of FR-011. It MUST NOT be sent for a restaurant with no website (FR-002) nor without a configured key.
- **FR-014**: The Unsplash key MUST stay on the backend: never sent to the browser, never logged.
- **FR-016**: The answer of Unsplash for one query MUST be reused for 1 hour (backend memory only, lost on restart), so that all restaurants of the same cuisine cost one request per hour.
- **FR-015**: A card showing a Unsplash photo MUST show the credit "Photo : <photographer> / Unsplash", the photographer name linking to the photographer profile and "Unsplash" linking to unsplash.com, both links carrying `utm_source=<application name>&utm_medium=referral` as the Unsplash guidelines ask (application name: a configuration value, default `reco_resto`), both opening in a new tab without counting as a reaction nor starting a drag.
- **FR-012**: The failure, slowness or absence of enrichment for one restaurant (website unreachable or too slow, Unsplash unavailable or refused, nothing found) MUST NOT show an error to the user and MUST NOT affect the other cards or the deck.

### Key Entities *(include if feature involves data)*

- **Restaurant**: as defined in features 002 and 008 (website address already present).
- **Card enrichment**: the photo found on the website of one restaurant, with its origin (declared sharing image, largest page image, or Unsplash cuisine photo with its credit); it may be missing. It is linked to one restaurant of the deck.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the reference search "Je veux manger à opera ce midi", the time between pressing "Commencer" and the first card being displayed is not longer with this feature than without it (mean of 3 runs).
- **SC-002**: In acceptance testing, 100% of restaurants with no website, an unreachable website or a website with no usable image show a card identical to feature 003, with no error message.
- **SC-003**: In acceptance testing, 100% of enrichments arriving after their card is shown leave the current card, the deck position and the reactions unchanged.
- **SC-004**: On the reference search "Je veux manger à opera ce midi" (3 runs), at least 50% of the deck cards of restaurants with a website show a real photo instead of the tablecloth. *(Proposed by Claude, approved by the product owner on 2026-11-26.)*
- **SC-005**: In acceptance testing, 0 image shown from enrichment is absent from the restaurant's website.

### Test plan

The Expected column is written by Claude and reviewed by the product owner (exception decided on 2026-11-26, as for spec 008).

| Test | Rule | Case | Input | Expected |
|---|---|---|---|---|
| T1 | FR-001 | nominal | Restaurant whose Google record has a website address | The response carries its website address |
| T2 | FR-001 | limit: no website | Restaurant whose Google record has no website address | No website address in the response; no enrichment, no link |
| T3 | FR-002 | limit: invalid address | Website value that is not an `http`/`https` address (for example `ftp://…` or `monresto`) | No enrichment request, no link; card as in feature 003 |
| T4 | FR-002 | limit: photo already known | Restaurant with a photo from the data source and a website declaring a sharing image | The data source photo is kept; no enrichment request is sent |
| T5 | FR-003 | nominal: sharing image | Restaurant with no photo, website declares a displayable sharing image | The card shows that image; the page images are not considered |
| T6 | FR-003 | limit: relative sharing image address | Sharing image declared with an address relative to the page | The address is resolved against the page address and the image is shown |
| T7 | FR-003 | limit: broken sharing image | Website declares a sharing image that cannot be displayed | Treated as no sharing image: the largest page image is used (T8) |
| T8 | FR-004 | nominal: largest image | Website declares no sharing image; page images declared 300x200 (dish), 1200x800 (room) and 400x100 (`logo.png`) | The card shows the 1200x800 room image |
| T9 | FR-004 | limit: largest image broken | The largest candidate cannot be displayed, the second one can | The card shows the second candidate |
| T10 | FR-005 | limit: only excluded images | Website with no sharing image; page images are `logo.svg`, `favicon.ico`, a 50x50 image and `brand-logo.png` | The card keeps the tablecloth, no error shown |
| T11 | FR-005 | limit: no image at all | Website page with no image | The card keeps the tablecloth, no error shown |
| T12 | FR-004 | limit: no declared size | Website with no sharing image; 3 images with no declared size, the first one cannot be displayed | The card shows the second image of the page |
| T13 | FR-007 | nominal | Card of a restaurant with a website | A link icon is on the card front; it opens the website in a new tab |
| T14 | FR-007 | limit: no website | Card of a restaurant with no website | No link icon |
| T15 | FR-008 | nominal | Deck at card 3 with 2 reactions; user taps the link icon then comes back to the tab | Same card 3 shown, the 2 reactions kept, no reaction added |
| T16 | FR-008 | limit: tap does not drag | User taps the link icon of the current card | The card does not move and no swipe is triggered |
| T17 | FR-009 | nominal | Same search with enrichment and without enrichment; time to the first card | Same time within measurement noise (SC-001) |
| T18 | FR-009 | limit: very slow websites | Search whose restaurants all have websites that answer after 30 s | The deck opens at the usual time, with tablecloths; each card stays on the tablecloth |
| T19 | FR-010 | nominal: arrives after card shown | Card 1 is shown; its enrichment arrives 2 s later | Card 1 shows the photo in place; current card and position unchanged |
| T20 | FR-010 | limit: arrives after reaction | User liked card 2 before its enrichment arrived | Like kept; card 2 shows the photo if the user rewinds to it |
| T21 | FR-010 | limit: arrives while dragging | Enrichment of the current card arrives while the user drags it | The drag goes on unchanged; the photo is shown without resetting the card position |
| T22 | FR-010 | limit: refinement | Refinement (feature 003) brings a new restaurant with a website into the deck | Its card is enriched in the same way |
| T23 | FR-010 | limit: rewind | User rewinds to a card whose enrichment arrived while it was hidden | The card shows its photo |
| T24 | FR-011 | limit: website slow | Website answers after 6 s | Enrichment abandoned at 5 s; card keeps the tablecloth, no error shown |
| T25 | FR-011 | limit: image check slow | Website answers in 1 s with no sharing image; the largest image answers after 6 s | Enrichment abandoned at 5 s; card keeps the tablecloth |
| T26 | FR-012 | limit: website unreachable | Restaurant whose website does not answer (connection refused) | Card keeps the tablecloth, no error shown |
| T27 | FR-004 | limit: no language model | Any enrichment, with the language model unreachable | Enrichment works the same; no request is sent to the language model |
| T28 | FR-004 | limit: more than 3 broken candidates | Website with no sharing image; the 4 largest images cannot be displayed, the 5th can | Card keeps the tablecloth (at most 3 checked), no error shown |
| T29 | FR-012 | limit: one fails, others succeed | Deck of 3 cards with websites, the enrichment of card 2 fails | Cards 1 and 3 get their photo; card 2 keeps the tablecloth; the deck goes on |
| T31 | FR-013 | nominal: Unsplash photo | Italian restaurant with a website with no usable image; Unsplash answers 15 photos | The card shows one of the 15 photos, with its credit; the query is "Restaurant italien", lang fr |
| T32 | FR-013 | limit: no website | Restaurant with no website and no photo | No Unsplash request; card as in feature 003 |
| T33 | FR-013 | limit: no result | Unsplash answers no photo | Card keeps the tablecloth, no error shown |
| T34 | FR-013 | limit: Unsplash fails | Unsplash answers 429, 5xx or is unreachable | Card keeps the tablecloth, no error shown; the failure is logged without the key |
| T35 | FR-013 | limit: site image found | Website declares a displayable sharing image | No Unsplash request |
| T36 | FR-013 | limit: no key | No Unsplash key configured | No Unsplash request; the rest of enrichment works |
| T37 | FR-013 | limit: same cuisine | Two Italian restaurants with different websites, Unsplash answers the same 15 photos | Each card gets the photo at its own hash position; the same website always gets the same photo |
| T38 | FR-013 | limit: unknown cuisine | Restaurant whose cuisine is empty | The query is "restaurant" |
| T39 | FR-014 | security | Any Unsplash request | The key is sent only in the request header to Unsplash; it appears in no response to the browser and in no log |
| T40 | FR-015 | nominal: credit | Card with a Unsplash photo of photographer "Jane Doe" | "Photo : Jane Doe / Unsplash" is shown; both links open in a new tab; tapping them is not a reaction and does not drag |
| T41 | FR-015 | limit: website photo | Card with a photo from the website | No Unsplash credit is shown |
| T42 | FR-016 | nominal: reuse | Two Italian restaurants enriched 10 minutes apart | One Unsplash request only |
| T43 | FR-016 | limit: expiry | Two Italian restaurants enriched 61 minutes apart | Two Unsplash requests |
| T30 | FR-002 | limit: redirect | Website redirects to another page that declares a sharing image | The redirect is followed and the card shows that image |

## Assumptions

- The website address comes only from Google Places (spec 008, `websiteUri`); no other source of websites is searched.
- Only the page at the website address (after redirects) is read; links to other pages of the site are not followed.
- Which restaurants are enriched first and how many at the same time, and whether enrichment results are kept between searches, are technical choices of `/speckit-plan`; this spec only requires deck cards to be enriched.
- The card shows no loading indicator while its enrichment is in progress; it shows the tablecloth until a photo arrives.
- Image rights for images taken from restaurant websites are accepted by the team for the proof of concept (product owner decision).
- No language model is used by enrichment (amendment (a)); the only external service besides the website is the Unsplash API (amendment (b)).
- The Unsplash key (`UNSPLASH_ACCESS_KEY`) is provided by the team in the environment, like the other keys. The Unsplash free "demo" limit is 50 requests per hour (2026-11-26); results of one cuisine query are kept in backend memory for 1 hour (FR-016), so the limit counts distinct cuisines, not cards; above it, cards keep the tablecloth.
- Unsplash photos are free to use under the Unsplash license; images are loaded directly from the Unsplash image host (hotlinking is what Unsplash asks for); only photo addresses and credits are kept in memory, never the images.

### Out of scope

- The "À goûter" dish, keywords, price level, menu, opening hours, phone or any other field from the website (product owner, 2026-11-26: photo only).
- Any change to the search itself (features 002 and 008): criteria, ranking, filtering, time to results.
- The card back of feature 004; features 005 (results map screen) and 006 (route to restaurant).
- Enrichment from sources other than the restaurant's own website and the Unsplash cuisine photo of FR-013 (web or image search engines, review sites, social networks).
- Following links to other pages of the website.
- Choosing the image with a language model (amendment (a), 2026-11-26).
- Unsplash photo for restaurants with no website (amendment (b)).
- Paid image search APIs (Brave) and Google Street View (amendment (b)).
- Editing or reporting a wrong photo by the user.
