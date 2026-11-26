# Feature Specification: Google Places Restaurant Source

**Feature Branch**: `feat/google-places-source`

**Created**: 2026-11-25

**Status**: Approved (product owner, 2026-11-25; self-approved for the POC, exception to the teammate approval of `specs/README.md`). Test plan Expected column still to be written by the product owner.

**Input**: User description: "Replace the restaurant data source of feature 002 (OpenStreetMap through the public Overpass API) by the Google Places API (New). Measured locally on 2026-11-25: the public Overpass server takes 35–51 s per query around Opéra (2.5 km) and answers 502 'at capacity' about one time in two, so the search took 39–47 s on average and often returned nothing; language-model calls take about 1 s each. The language model also wrote invalid Overpass arguments (invented tag filters) and sometimes an unreadable final answer (prose or a code fence around the JSON, missing `centers`, invalid JSON). Goals: a fast search (target for the reference sentence 'Je veux manger à opera': under 20 s, mean of 3 runs), fewer language-model generation errors, and card data that is mostly empty today: photo, opening hours, price level, website, and diet indicators when Google provides them. The language model stays for understanding the sentence but no longer writes data-source queries. Robust reading of the language model's final answer is part of this feature. Spec 007 is kept as a complement." Decided by the product owner on 2026-11-25.

**Feature set**: this spec changes the restaurant data source of feature 002 (natural-language search). It feeds features 003 (swipe deck, card front), 004 (card back), 005 (results map screen) and 006 (route to restaurant), and is complemented by feature 007 (card website enrichment). It does not edit those specs; its impacts on them are listed in "Impact on other features".

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get my deck quickly (Priority: P1)

Philipe types "Je veux manger à opera" and presses "Commencer". Today he waits 39–47 s on average and often gets nothing because the restaurant source is saturated. With this feature, the restaurants come from Google Places and his first card appears in under 20 s.

**Why this priority**: a search that is slow and fails one time in two makes Nappe unusable; speed and reliability are the main reason for this feature.

**Independent Test**: run the reference sentence "Je veux manger à opera" three times end to end, measure the time from pressing "Commencer" to the first card, and check that every run returns a deck.

**Acceptance Scenarios**:

1. **Given** the sentence "Je veux manger à opera", **When** it is submitted three times, **Then** the mean time between pressing "Commencer" and the first card being displayed is under 20 s, and every run returns a deck.
2. **Given** any sentence understood by feature 002, **When** results are returned, **Then** every restaurant comes from Google Places and no restaurant comes from OpenStreetMap.
3. **Given** a sentence understood by feature 002, **When** results are returned, **Then** the rules of feature 002 still apply: radius (default or from the sentence), open at the targeted moment, deck size, several places merged without duplicates, address required, France only.
4. **Given** the frontend of features 001 and 003 unchanged, **When** a search succeeds, **Then** the deck opens as before (the response format stays compatible, see FR-011).

---

### User Story 2 - See richer cards (Priority: P1)

Today most cards show the tablecloth, no price level and no opening hours, because OpenStreetMap rarely has them. With this feature, Philipe sees on each card a real photo, the opening hours, the price level, and diet indicators when Google provides them; the restaurant's website is known too.

**Why this priority**: the card front of feature 003 is mostly empty today; filling it is the second goal of the product owner.

**Independent Test**: run reference searches and check, for each returned restaurant, which of photo, weekly opening hours, price level, website, phone and diet indicators are present, compared with what Google Places holds for that place.

**Acceptance Scenarios**:

1. **Given** Google Places has a photo for a restaurant, **When** its card is shown, **Then** the card shows that photo.
2. **Given** Google Places has no photo for a restaurant, **When** its card is shown, **Then** the default tablecloth visual is shown (feature 003, US1 AS3).
3. **Given** Google Places has the weekly opening hours, the price level, the website or the phone number of a restaurant, **When** results are returned, **Then** each of them is provided for that restaurant.
4. **Given** Google Places lacks one of these fields for a restaurant, **When** results are returned, **Then** that field is absent and the screens apply their own fallback (features 003, 004, 006); no value is invented.
5. **Given** Google Places states that a restaurant serves a diet (for example vegetarian food), **When** results are returned, **Then** that diet is listed for the restaurant.
6. **Given** Google Places says nothing about a diet for a restaurant, **When** results are returned, **Then** that diet is not listed for the restaurant and the restaurant is not described as not serving it.

---

### User Story 3 - Be understood even when the language model answers clumsily (Priority: P1)

Philipe types a sentence the language model understands well, but the model wraps its answer in prose or a code block, leaves out a field, or writes broken JSON. Today this ends with "rephrase your sentence". With this feature, a well-understood sentence is never turned into "not understood" because of the form of the answer.

**Why this priority**: these errors were observed on 2026-11-25 and make a correct sentence fail; the product owner moved this need into this feature.

**Independent Test**: replay recorded faulty answers of the language model (text around the JSON, code fence, missing optional fields, missing search points, invalid JSON) for sentences that are understood, and check the search outcome for each.

**Acceptance Scenarios**:

1. **Given** the language model's final answer contains the expected answer surrounded by text or inside a code block, **When** it is read, **Then** the search goes on as if the answer had no surrounding text.
2. **Given** the final answer leaves out optional fields (for example moment, distance, cuisines), **When** it is read, **Then** the defaults of feature 002 apply (moment "now", default radius, no cuisine constraint) and the sentence is not reported as not understood.
3. **Given** the final answer names a place but gives no usable position for it, **When** it is read, **Then** the system locates the place by itself and the search goes on.
4. **Given** the final answer cannot be read at all (invalid or empty), **When** it is read, **Then** the user is not told that the sentence was not understood (see FR-007 for the outcome).
5. **Given** the language model states that the sentence is not a restaurant request (for example "blablabla"), **When** it is read, **Then** the user is invited to rephrase, as in feature 002 (US3 AS1).

---

### User Story 4 - Know what to do when Google cannot answer (Priority: P2)

Google Places may be unavailable, slow, out of quota, or not configured (no API key). Philipe gets a clear outcome instead of an empty or broken screen.

**Why this priority**: it protects the experience and the budget, but is not needed to demonstrate the main value.

**Independent Test**: simulate each failure (service down, timeout, quota exceeded, missing or invalid key) and check the outcome shown to the user.

**Acceptance Scenarios**:

1. **Given** Google Places is unavailable or does not answer in time, **When** a sentence is submitted, **Then** the outcome of FR-012 is shown.
2. **Given** the monthly free cap or the billing quota is exceeded, **When** a sentence is submitted, **Then** the outcome of FR-012 is shown.
3. **Given** no API key or an invalid API key is configured, **When** a sentence is submitted, **Then** the outcome of FR-012 is shown, and the problem is visible to the team in the backend logs without the key itself being logged.
4. **Given** Google Places answers with no restaurant for the search, **When** results are returned, **Then** the "no restaurant found" message of feature 002 (US3 AS2) is shown.

---

### User Story 5 - Respect the Google Maps Platform terms (Priority: P2)

The team must be allowed to show Google data in Nappe. The terms researched on 2026-11-25 (to be confirmed by the team against the current terms) require an attribution when Google content is shown without a Google map, restrict the use of Google content with maps in the European Economic Area, and limit how long Google content may be kept.

**Why this priority**: not respecting the terms can lead to the API being cut off; it shapes features 005 and 006.

**Independent Test**: review every screen showing Google-sourced data for the attribution, check the map screens against the map restriction, and check that no Google content is kept beyond the allowed durations.

**Acceptance Scenarios**:

1. **Given** a screen shows Google-sourced restaurant data without a Google map, **Then** the Google attribution is shown on that screen.
2. **Given** a screen shows a map, **Then** the only Google content used with that map is latitude, longitude and place identifier (see FR-015).
3. **Given** Google content is kept by the system, **Then** it is kept no longer than the allowed durations (see FR-016).

---

### Edge Cases

- A Google place has no address: it is not returned for the deck (feature 002, FR-016).
- A Google place is permanently or temporarily closed: it is not returned for the deck (feature 002, FR-007: open at the targeted moment).
- A Google place has no opening hours: it is treated as in feature 002 today (restaurants with unknown hours are kept, see Assumptions).
- A Google place is not a restaurant (for example a bar or a bakery with food): see Assumptions on the place types kept.
- More than the deck size match: the deck has 15 restaurants (feature 002, FR-008); the size of the zone list of feature 002 FR-013 is bounded by what the source can return per search (see FR-017).
- The sentence names several places (for example "entre Opéra et Châtelet"): each place is searched and results are merged without duplicates (feature 002, FR-015); the same Google place found around two places appears once.
- The sentence asks for a diet that Google does not state for a place: see FR-009 and Assumptions.
- The sentence asks for a cuisine that has no direct equivalent in Google's place types (for example "asiatique"): the search still returns restaurants of that cuisine; how the cuisine is matched is a plan-level question.
- The photo of a place exists but cannot be displayed: the card shows the tablecloth visual (feature 003, US1 AS3).
- The language model's answer contains several JSON objects or text with braces: see FR-004 and Assumptions.
- The place is outside France: "Nappe n'est disponible qu'en France pour l'instant" (feature 002, FR-010), and no paid request is made for that sentence.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST take the restaurants of every search from Google Places; OpenStreetMap through Overpass MUST no longer be used as the restaurant source of the search.
- **FR-002**: The language model MUST only understand the sentence (cuisines, diets, AND/OR grouping, places, moment, distance or time on foot, place outside France, not a restaurant request); it MUST NOT write queries, filters or arguments for the restaurant data source. The search sent to the source MUST be built by the system from the understood criteria.
- **FR-003**: The rules of feature 002 MUST keep applying with the new source: FR-001 to FR-016 of feature 002 (criteria, AND/OR, radius and default radius, distance and time on foot, device position, open at the targeted moment, deck size, France only, failure messages, restaurant data, zone list, several places merged without duplicates, address required).
- **FR-004**: When the language model's final answer contains the expected answer surrounded by other text (prose, code block markers), the system MUST read the expected answer and ignore the surrounding text.
- **FR-005**: When the final answer leaves out optional fields, the system MUST apply the defaults of feature 002 (moment "now", default radius, no cuisine or diet constraint) and MUST NOT report the sentence as not understood.
- **FR-006**: When the final answer names one or more places without a usable position for them, the system MUST locate those places by itself and go on with the search; it MUST NOT report the sentence as not understood for that reason.
- **FR-007**: When the final answer cannot be read at all (invalid, truncated or empty), the system MUST NOT report the sentence as not understood; it MUST try to recover a readable answer, and when it cannot, MUST show the "service unavailable" outcome of feature 002 (US3 AS4: message and "Réessayer"). "Not understood" MUST be reserved for a readable answer stating that the sentence is not a restaurant request.
- **FR-008**: For each restaurant, the system MUST provide from Google Places, each when Google provides it: name, location, address, distance from the nearest search place, cuisine type, photo, weekly opening hours, price level, website address, phone number and diets served. The set of Google fields requested MUST be the one needed for the restaurant data already defined by feature 002 (FR-012), with no change of behavior (product owner, 2026-11-25); the resulting billing tier is recorded in the plan. A field Google does not provide MUST stay absent and MUST NOT be invented.
- **FR-009**: A diet MUST be listed for a restaurant only when Google Places states that the restaurant serves it; when Google says nothing about a diet, the restaurant MUST NOT be described as not serving it.
- **FR-010**: The photo of a restaurant MUST be displayable by the frontend without exposing the API key; a restaurant with no photo, or whose photo cannot be displayed, MUST show the default tablecloth visual (feature 003, US1 AS3).
- **FR-011**: The response of `POST /api/search` (contract of feature 002) MUST stay compatible with the current frontend: no existing field is removed, renamed or changes meaning. The only planned contract change is the addition of an optional website address per restaurant; any other addition needed by this spec (for example attribution data for FR-014 or a place identifier) MUST be optional and listed in the plan. Fields Google does not provide (keywords, signature dish, menu) stay absent as today.
- **FR-012**: When Google Places is unavailable, does not answer in time, refuses the request because a quota is exceeded, or cannot be called because the API key is missing or invalid, the system MUST show the "service unavailable" message with "Réessayer" of feature 002 (US3 AS4) in all of these cases (product owner, 2026-11-25: option A). A missing or invalid key MUST be reported in the backend logs without logging the key.
- **FR-013**: The API key MUST stay on the server: it MUST NOT appear in any response, frontend asset, URL given to the browser or log line, and MUST NOT be committed to the repository.
- **FR-014**: Every screen that shows Google-sourced restaurant data without a Google map MUST show the Google attribution required by the Google Maps Platform terms (to be confirmed by the team against the current terms); photos MUST carry the author attribution Google provides with them when the terms require it.
- **FR-015**: Google content other than latitude, longitude and place identifier MUST NOT be used with any map, as required for the European Economic Area by the Google Maps Platform terms (to be confirmed by the team against the current terms). How features 005 and 006 comply is deferred: the team decides after confirming the terms with Google (product owner, 2026-11-25: option C); until then this rule only constrains 008, which shows no map.
- **FR-016**: The system MUST NOT keep Google content longer than the terms allow (as researched: latitude and longitude at most 30 days, place identifier without limit; other content as stated by the current terms, to be confirmed by the team).
- **FR-017**: The system MUST NOT return for one search more restaurants than the source returns for it (as researched: at most 20 results per page and 60 with pagination per Text Search); the deck (15 by default) and the zone list of feature 002 FR-013 are built from them.

- **FR-018**: The deck MUST hold at most one restaurant per chain: the nearest one (smallest distance; on equal distances, the first in the order of the results). The freed slots MUST go to the next restaurants of the zone, so the deck still holds the deck size when enough restaurants exist. The zone list of feature 002 FR-013 (results map) is unchanged. Two restaurants are the same chain when they share any of: (1) the website host (lower case, without a leading `www.`), except generic hosts shared by unrelated businesses (configured list: at least Facebook, Instagram, Linktree, Google, Tripadvisor, TheFork/LaFourchette, Uber Eats, Deliveroo), which never group restaurants; per-site subdomains (for example `*.wixsite.com`) keep their full host; (2) the normalized name (lower case, accents, punctuation and extra spaces removed, the part after " - ", " | " or "(" dropped: "Big Mamma - East Mamma" gives "big mamma"); (3) a configured known chain name starting the normalized name (whole words).

**Amendment (chains, product owner 2026-11-26)**: the deck showed several cards of the same chain (for example several Big Mamma or O'Tacos restaurants). Decision: one card per chain in the deck (FR-018); the zone list used by the results map keeps every restaurant. The initial list of known chain names in `application.yml` (`search.chains.known-names`) is a PROPOSAL to be confirmed by the product owner; the generic hosts are in `search.chains.generic-hosts`. Both lists are configuration. The Expected column of T48-T53 is derived from this decision.

### Key Entities *(include if feature involves data)*

- **Search criteria**: unchanged from feature 002 (cuisines, diets, AND/OR grouping, places, moment, distance limit, time on foot); produced by the language model, never containing data-source query text.
- **Language model answer**: what the model states it understood; may arrive wrapped in text, incomplete or unreadable; read according to FR-004 to FR-007.
- **Restaurant**: as defined in feature 002, now from Google Places, extended with its website address and, when needed by the terms, its Google place identifier and photo attribution.
- **Google attribution**: the mention required by the terms when Google content is shown without a Google map, and the author attribution of each photo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the reference sentence "Je veux manger à opera", the time between pressing "Commencer" and the first card being displayed is under 20 s, mean of 3 runs (target of the product owner, 2026-11-25; measured 39–47 s with the previous source).
- **SC-002**: On the same 3 runs, 3 out of 3 searches return a deck (the previous source returned nothing about one time in two).
- **SC-003**: On a set of recorded language-model answers that are well understood but wrapped in text, in a code block, or missing optional fields or search points, 0 search ends as "not understood".
- **SC-004**: On the reference sentences, the share of deck cards with a photo, with opening hours and with a price level is higher than with the previous source. Reference sentence "Je veux manger à opera"; target: at least 80 % of the deck cards have a photo, opening hours and a price level all at once on the same card (product owner, 2026-11-25). **Amended 2026-11-26 (product owner)**: Google returns no photo for this project (field `photos` absent on every place in local runs, probably the EEA terms); the photo is removed from this criterion and measured by spec 007 (photo taken from the restaurant website). Target now: at least 80 % of the deck cards have opening hours and a price level all at once.
- **SC-005**: In acceptance testing, 100% of returned restaurants are within the radius, have an address and are not known to be closed at the targeted moment (same rule as feature 002, SC-002).
- **SC-006**: In a review of responses, frontend assets and logs, the API key appears 0 times.
- **SC-007**: In acceptance testing, 100% of screens showing Google-sourced data without a Google map show the Google attribution.

### Test plan

The Expected column was written by Claude at the explicit request of the product owner (2026-11-25, override of the rule "Expected written by the product owner only" for this spec), derived from the rule "same behavior as with Overpass, only the data source changes". T14, T27, T28 and T44 involve a judgment and are to be checked by the product owner.

| Test | Rule | Case | Input | Expected |
|---|---|---|---|---|
| T1 | FR-001 | nominal | Sentence "Je veux manger à opera"; inspect the origin of every returned restaurant | Every restaurant of the deck comes from Google Places; no Overpass query is made |
| T2 | FR-001 | limit: Overpass down | Same sentence with the Overpass service unreachable | Same deck as when Overpass is reachable: Overpass is not used |
| T3 | FR-002 | nominal | Sentence "J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris"; inspect the understood criteria and what the language model produced | Same criteria as with Overpass (place Opéra, asian and/or vegetarian kept as written); the model output holds only criteria and places, no data-source query |
| T4 | FR-002 | limit: model tries to write a query | Recorded language-model answer containing a data-source query or tag filters in addition to the criteria | The query or filters are ignored; the search uses the criteria only and the deck is shown as usual |
| T5 | FR-003 | nominal: radius and moment | Sentence "italien ce soir près de la gare de Lyon" | Deck of Italian restaurants open tonight within the default radius (2.5 km) of Gare de Lyon, as with Overpass |
| T6 | FR-003 | limit: time on foot | Sentence "un restaurant à moins de 10 minutes à pied d'Opéra" | Only restaurants within 10 minutes on foot (walking speed of 002, 5 km/h: about 833 m) of Opéra |
| T7 | FR-003 | limit: several places | Sentence "entre Opéra et Châtelet", a place found around both | Restaurants around both places; distance to the nearest place; a place found around both appears once |
| T8 | FR-003 | limit: outside France | Sentence "manger italien à Rome" | Outside-France message of 002, no deck |
| T9 | FR-003 | limit: no place, position given | Sentence "un bon thaï ce soir" with a device position | Thai restaurants open tonight around the device position |
| T10 | FR-003 | limit: place without address | Google returns a matching place with no address | The place is excluded from the deck (002 FR-016) |
| T11 | FR-003 | limit: more than deck size | Google returns more matching places than the deck size | The deck holds the deck size (15) as with Overpass; the other places go to the zone |
| T12 | FR-004 | limit: text around the answer | Recorded final answer: prose sentence, then the answer, then another sentence | The answer is read and the search goes on normally |
| T13 | FR-004 | limit: code block | Recorded final answer inside a code block marked as JSON | The answer is read and the search goes on normally |
| T14 | FR-004 | limit: several objects | Recorded final answer containing two JSON objects | One object is read and the search goes on; never "not understood" |
| T15 | FR-005 | limit: missing optional fields | Recorded final answer with understood=true and only the place (no moment, no distance, no cuisine) | The defaults of 002 apply (moment now, default radius) and the search goes on |
| T16 | FR-006 | limit: missing search points | Recorded final answer naming "Opéra, Paris" with no `centers` | The system locates "Opéra, Paris" itself and the search goes on; never "not understood" |
| T17 | FR-006 | limit: invalid position | Recorded final answer with a position outside valid coordinates for a named place | The invalid position is ignored, the place is located by the system and the search goes on |
| T18 | FR-007 | limit: invalid JSON | Recorded final answer with a missing closing brace | A recovery is attempted; if the answer stays unreadable, "service unavailable" with "Réessayer"; never "not understood" |
| T19 | FR-007 | limit: empty answer | Recorded final answer that is empty | A recovery is attempted; if the answer stays unreadable, "service unavailable" with "Réessayer"; never "not understood" |
| T20 | FR-007 | nominal: not a request | Sentence "blablabla" | "Not understood" message of 002 |
| T21 | FR-008 | nominal: complete place | Search returning a place for which Google has photo, hours, price level, website and phone | The card shows the Google photo, the opening tile and the price level; website and phone are in the response |
| T22 | FR-008 | limit: place with no photo | Google has no photo for a returned place | Tablecloth visual (003 US1 AS3) |
| T23 | FR-008 | limit: place with no hours | Google has no opening hours for a returned place | Opening tile hidden (003 FR-022); the restaurant is kept, as with Overpass |
| T24 | FR-008 | limit: place with no price level | Google has no price level for a returned place | No price chip |
| T25 | FR-008 | limit: place with no website | Google has no website for a returned place | No website in the response for that place; the rest of the card is unchanged |
| T26 | FR-009 | nominal: diet stated | Sentence "végétarien près d'Opéra", Google states that a returned place serves vegetarian food | The place is shown with the vegetarian diet |
| T27 | FR-009 | limit: diet not provided | Sentence "végétarien près d'Opéra", Google says nothing about vegetarian food for a matching place | As with Overpass, a vegetarian search keeps only places stated as vegetarian; the place is never described as not vegetarian |
| T28 | FR-009 | limit: diet with no Google indicator | Sentence "halal près d'Opéra" (diet for which Google may have no indicator) | As with Overpass, only places stated as halal are kept; when Google has no halal indicator, no place is labelled halal and the outcome follows 002 (no result if nothing matches) |
| T29 | FR-010 | nominal | Card of a place with a Google photo; inspect the photo address given to the browser | The photo address given to the browser does not contain the API key |
| T30 | FR-010 | limit: photo not displayable | Google photo of a place cannot be loaded | Tablecloth visual |
| T31 | FR-011 | nominal | Current frontend (features 001, 003) against the new backend, reference sentence | The current frontend shows the deck without any change |
| T32 | FR-011 | limit: new website field | Response for a place with a website, read by the current frontend | The current frontend ignores the website field without error |
| T33 | FR-012 | limit: Google unavailable | Google Places unreachable when a sentence is submitted | "Service unavailable" with "Réessayer" |
| T34 | FR-012 | limit: Google too slow | Google Places answers after the configured time limit | "Service unavailable" with "Réessayer" |
| T35 | FR-012 | limit: quota exceeded | Google Places refuses the request because a quota is exceeded | "Service unavailable" with "Réessayer" |
| T36 | FR-012 | limit: missing key | No API key configured, sentence submitted | "Service unavailable" with "Réessayer"; the backend log reports the missing key |
| T37 | FR-012 | limit: invalid key | Invalid API key configured, sentence submitted | "Service unavailable" with "Réessayer"; the backend log reports the invalid key without its value |
| T38 | FR-003 | limit: no result | Google Places returns no place for the search | "No result" message of 002 |
| T39 | FR-013 | nominal | Inspect responses, frontend assets, photo addresses and backend logs after a search | The key appears nowhere |
| T40 | FR-014 | nominal | Deck card front showing Google data | The Google attribution is shown |
| T41 | FR-014 | limit: photo attribution | Card showing a Google photo that comes with an author attribution | The photo author attribution is shown with the photo |
| T42 | FR-015 | nominal | Results map screen (feature 005) with Google-sourced restaurants | Deferred: decision on 005 pending (FR-015) |
| T43 | FR-015 | limit: route screen | Route screen (feature 006) for a Google-sourced restaurant | Deferred: decision on 006 pending (FR-015) |
| T44 | FR-016 | nominal | Inspect what the system keeps after a search (server and browser) | Nothing from Google is kept beyond the terms: place IDs only without limit, positions at most 30 days, nothing else stored |
| T45 | FR-017 | limit: many matches | Search around Opéra where Google has more than 60 matching places; deck and zone list requested | The deck holds the deck size; the zone holds at most what Google returns (Nearby Search, D3: at most 20 places per search point) |
| T46 | SC-001 | timing: reference sentence | Sentence "Je veux manger à opera", 3 runs, time from "Commencer" to the first card | Mean of the 3 runs under 20 s |
| T47 | SC-002 | reliability | Same 3 runs; number of runs returning a deck | 3 runs out of 3 return a deck |
| T48 | FR-018 | nominal: same website host | Zone with "East Mamma" (`https://www.bigmammagroup.com/east`, 800 m), "Libertino" (`http://bigmammagroup.com/libertino`, 300 m) and another restaurant | The deck holds "Libertino" and the other restaurant; the zone holds all three |
| T49 | FR-018 | limit: generic host | Two unrelated restaurants whose websites are on `facebook.com` (and two on `tripadvisor.*`) | All of them stay in the deck |
| T50 | FR-018 | limit: same normalized name | "Big Mamma - East Mamma" (900 m) and "BIG MAMMA \| Pink" (400 m) | Only "BIG MAMMA \| Pink" is in the deck |
| T51 | FR-018 | limit: known chain, different names | "O'Tacos Châtelet" (700 m) and "O Tacos République" (250 m), "O'Tacos" known | Only "O Tacos République" is in the deck; "Paulette" is not the chain "Paul" |
| T52 | FR-018 | limit: freed slots | Deck size 3; zone "Paul Opéra" 100 m, "Paul Louvre" 200 m, "A", "Paul Bastille", "B", "C" | Deck "Paul Opéra", "A", "B"; the zone is unchanged |
| T53 | FR-018 | limit: no website, no name | Restaurants without website and without a usable name | No grouping: all of them stay in the deck |

## Impact on other features

Notes for the product owner; the other specs are not edited by this feature.

### Feature 007 (card website enrichment) - kept as a complement

- **Boundary**: 008 provides, from Google Places, the photo and the website address of each restaurant (and the other card fields of FR-008). 007 keeps reading the restaurant's website after the deck is shown, mainly for the "À goûter" dish (007 US2, FR-006), and its rules (no delay of the search, card updated in place, no invented value) are unchanged.
- **Requirements of 007 that become redundant or change meaning** (for the product owner to decide; 007 is not edited here):
  - 007 FR-001 and the Assumption "the website address comes only from OpenStreetMap": the website address now comes from Google Places (this spec, FR-008); 007 only consumes it.
  - 007 US1 and FR-003 to FR-005 (photo taken from the website): Google provides the photo; by 007 US1 AS6 the data-source photo wins, so the website photo only remains a fallback for places Google has no photo for. Keep as a fallback or drop.
  - 007 US2 AS3 and FR-007 (other card fields from the website, still a [NEEDS CLARIFICATION] in 007): price level, opening hours and phone now come from Google; the open question of 007 shrinks to "which fields, if any, besides the dish".
  - 007 FR-009 and FR-010 (website link on the card front): still needed for display; only the source of the address changes.
  - 007 SC-004 (more photos than feature 003 alone): its baseline changes, since most photos now come from Google.

### Features 005 (results map screen) and 006 (route to restaurant)

- As researched on 2026-11-25 (to be confirmed by the team against the current terms), France being in the European Economic Area, Google Places content other than latitude, longitude and place identifier must not be used with any map. 005 shows on a map one point per restaurant with its list cards (photo, name, cuisine, status) next to it; 006 shows the restaurant information next to a route drawn on a map. Both may be affected, depending on the answer to FR-015.
- 005 and 006 currently assume an open-source map without an API key; this assumption may conflict with option B of FR-015.
- 006 US2 (open the route in Google Maps and other apps) is not restricted by this rule as far as researched.
- When no Google map is shown, these screens must also show the Google attribution (FR-014).

## Assumptions

- The data source is the Google Places API (New), decided by the product owner on 2026-11-25; the documentation reference is https://developers.google.com/maps/documentation/places/web-service/overview .
- An API key and a Google Cloud project with billing are required; the product owner does not have one yet. Who creates them, who owns the billing account and the monthly budget limit are to be decided by the team. *(To be validated.)*
- The API key is configured like the other secrets: in `.env` (ignored), with its variable name in `.env.example` (constitution). *(Convention of the code base.)*
- The language model stays the one of feature 002; whether it is still needed for every part of the understanding (for example locating places, FR-006) is decided at `/speckit-plan`.
- The constraints of the Google Maps Platform terms cited here (EEA map restriction, attribution, caching durations, billing tiers and free caps of Pro 5,000/month and Enterprise 1,000/month, 20 results per page and 60 with pagination) come from research done on 2026-11-25 and are to be confirmed by the team against the current terms. *(To be validated.)*
- The number of results requested from Google per search (within the limit of FR-017), and therefore the size of the zone list of feature 005, is set by the team at `/speckit-plan`. *(To be validated.)*
- The time after which Google Places is considered not answering (FR-012) is a configurable value set by the team at `/speckit-plan`. *(To be validated.)*
- Whether and how long Google results are kept between searches (cache), within the limits of FR-016, is decided at `/speckit-plan`; the backend of feature 002 is stateless today. *(To be validated.)*
- How many recovery attempts are made for an unreadable language-model answer (FR-007), and whether a recovery is a new call to the model, is decided at `/speckit-plan`. *(To be validated.)*
- When the answer contains several JSON objects (T14), which one is read is decided at `/speckit-plan`. *(To be validated.)*
- When a place named in the sentence cannot be located at all (FR-006), the outcome is the "place required" outcome of feature 002 (the user is asked to specify a place). *(To be validated.)*
- A restaurant whose diet is unknown to Google is kept when the sentence asks for that diet with OR, and the rule for AND (keep or exclude) is decided by the product owner; today feature 002 has no unknown-diet rule. *(To be validated.)*
- Restaurants with unknown opening hours are kept, as decided for feature 002 on 2026-11-25 (deviation from 002 FR-007 already reported in `RestaurantFilters`). *(To be validated for the new source.)*
- Which Google place types count as a restaurant (restaurant only, or also cafés, bars, bakeries serving food) is decided by the product owner at `/speckit-plan`. *(To be validated.)*
- The form, wording and placement of the Google attribution on each screen (FR-014) are decided at `/speckit-plan` against the current Google attribution requirements. *(To be validated.)*
- The price level is shown in the existing format of feature 003 (for example "€€"); how Google's price levels map to it is decided at `/speckit-plan`. *(To be validated.)*
- Privacy: the understood place and cuisines (and possibly the device position) are sent to Google; this must be checked by the team, as for the third parties of feature 002 (002 plan, O5). *(To be validated.)*
- The relevance rule of feature 002 (002 research R5, still open) is not defined here; whether Google's own ranking or rating may be used is a plan-level question.

### Out of scope

- Any change to the user-visible criteria, messages and screens of features 001 to 006, except the Google attribution (FR-014) and the compliance of maps (FR-015, decided with the product owner).
- Editing specs 002, 005, 006 or 007; their impacts are listed in "Impact on other features".
- Reading the restaurant website (dish "À goûter", website photo fallback): feature 007.
- Google ratings, reviews, review summaries and AI-generated place descriptions: not requested by the product owner.
- Refinement of the deck after reactions (feature 003, US3) beyond using the same source.
- Removing or keeping the local Overpass instance (`infra/overpass`) and the Overpass code: a plan and cleanup decision.
- Places outside France (feature 002, FR-010).
- Creating the Google Cloud project, the API key and the billing account.
