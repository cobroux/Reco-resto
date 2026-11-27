# Feature Specification: Natural-Language Restaurant Search

**Feature Branch**: `feat/natural-language-search`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "Spec 2 – Back search bar. As a user, I want to describe what I want to eat in natural language in order to get suitable proposals. Example: 'J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris'." MVP persona: "Philipe, 38, employee and tourist in Paris, wants to find a restaurant matching his tastes and diet."

**Feature set**: this spec is 2 of 6 for the MVP: 001 home search screen → 002 natural-language search → 003 swipe deck → 004 restaurant card details → 005 results map screen → 006 route to restaurant.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get restaurants that match my sentence (Priority: P1)

Philipe submits a sentence from the home screen (feature 001). Nappe understands what he asks for — type of cuisine, diet, place and moment — and returns a set of restaurants that match, ranked by relevance, ready to be shown as a swipe deck (feature 003).

**Why this priority**: it is the core value of Nappe: turning a free sentence into relevant restaurants.

**Independent Test**: submit a set of reference sentences (for example "asiatique et/ou végétarien dans la zone de Opéra à Paris", "italien ce soir près de la gare de Lyon") and check, for each, the understood criteria and that every returned restaurant matches them.

**Acceptance Scenarios**:

1. **Given** the sentence "J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris", **When** it is submitted, **Then** the understood criteria are: cuisine "asian", diet "vegetarian", combined with OR, place "Opéra, Paris", moment "now".
2. **Given** the sentence "asiatique et végétarien près d'Opéra", **When** it is submitted, **Then** cuisine and diet are combined with AND, and every returned restaurant is both Asian and vegetarian-friendly.
3. **Given** a sentence with a place, **When** results are returned, **Then** every restaurant is within the search radius of that place.
4. **Given** a sentence with no distance, **When** results are returned, **Then** the search radius is the configured default (2.5 km).
5. **Given** a sentence with a distance (for example "à moins de 10 minutes à pied"), **When** results are returned, **Then** that distance replaces the default radius.
6. **Given** a sentence with a moment (for example "ce soir", "samedi midi"), **When** results are returned, **Then** every restaurant is open at that moment.
7. **Given** a sentence with no moment, **When** results are returned, **Then** every restaurant is open now.
8. **Given** more matching restaurants than the configured deck size (15 by default), **When** results are returned, **Then** exactly the deck size is returned, the most relevant first.
9. **Given** fewer matching restaurants than the deck size, **When** results are returned, **Then** all of them are returned.

---

### User Story 2 - Use my position when I do not say where (Priority: P2)

Philipe types "un bon thaï ce soir" without a place. Nappe asks for the device position and searches around it. If he refuses, Nappe asks him to specify a place.

**Why this priority**: a tourist often wants "around me"; the search still works without it as long as the sentence contains a place.

**Independent Test**: submit a sentence without a place, accept then refuse the position request, and check both behaviors.

**Acceptance Scenarios**:

1. **Given** a sentence with a place, **When** it is submitted, **Then** the device position is not requested.
2. **Given** a sentence without a place, **When** it is submitted, **Then** the device position is requested and, if granted, used as the search place.
3. **Given** a sentence without a place, **When** the user refuses or the position is unavailable, **Then** the user is asked to specify a place, and no search is run until a place is given.

---

### User Story 3 - Know what to do when the search fails (Priority: P3)

When Nappe cannot answer, Philipe gets a clear message that tells him what to do next.

**Why this priority**: it protects the experience but is not needed to demonstrate the main value.

**Independent Test**: trigger each failure case below and check the message and the proposed action.

**Acceptance Scenarios**:

1. **Given** a sentence that cannot be understood (for example "blablabla"), **When** it is submitted, **Then** the user is invited to rephrase, with an example of sentence.
2. **Given** a sentence understood but with no matching restaurant, **When** results are returned, **Then** the user is told no restaurant was found and is invited to refine the search.
3. **Given** a place outside France, **When** it is submitted, **Then** the message "Nappe n'est disponible qu'en France pour l'instant" is shown.
4. **Given** the search service is unavailable, **When** a sentence is submitted, **Then** an error message and a "Réessayer" button are shown, and pressing "Réessayer" runs the same search again.

---

### Edge Cases

- The sentence mixes AND and OR ("asiatique ou italien, et végétarien"): the criteria keep the grouping expressed by the user.
- The sentence contains several places (for example "entre Opéra et Châtelet", "à Montmartre ou près de la gare du Nord"): the search runs around every place named and the results are merged into one set, ranked by relevance (see FR-015).
- The sentence asks for a budget or a specific dish: these are not search criteria in the MVP; they are ignored, and the rest of the sentence is used. *(Amended 2026-11-26: a dish now gives the cuisines that serve it, see FR-003.)*
- The moment is in the past ("hier soir"): handled like a sentence that cannot be understood (invitation to rephrase).
- Restaurants closed at the targeted moment are never returned for the deck.
- Restaurants with no known address are never returned for the deck, since no route can be offered (see FR-016).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST extract from the sentence these criteria when present: type of cuisine, diet, place, moment, and a distance limit.
- **FR-002**: The system MUST distinguish AND from OR between criteria, as expressed in the sentence.
- **FR-003**: The system MUST NOT use budget or a specific dish as search criteria. **Amended 2026-11-26 (product owner)**: a specific dish or a craving (for example "frites", "donburi", "épicé") is turned into the cuisine types most likely to serve it (spec 008 closed list); the budget is still ignored.
- **FR-004**: The system MUST search restaurants within a radius around the place; the default radius MUST be 2.5 km and MUST be configurable without code change.
- **FR-005**: A distance expressed in the sentence MUST replace the default radius for that search.
- **FR-006**: When the sentence has no place, the system MUST request the device position and use it; if the position is refused or unavailable, the system MUST ask the user to specify a place.
- **FR-007**: The system MUST return only restaurants open at the targeted moment; the targeted moment is the one in the sentence, or now when none is given. The moment MAY be on another day than today.
- **FR-008**: The system MUST return at most the deck size, configurable without code change, 15 by default, ranked by relevance.
- **FR-009**: The system MUST return all matching restaurants when there are fewer than the deck size.
- **FR-010**: The system MUST cover places in France only, and MUST show "Nappe n'est disponible qu'en France pour l'instant" for a place outside France.
- **FR-011**: The system MUST handle the failure cases of User Story 3 with the stated messages and actions.
- **FR-012**: For each restaurant, the system MUST provide the data needed by features 003 to 006: name, location, distance, type of cuisine, diets satisfied, keywords, price level, opening hours of the week, photo, a dish one can eat there, address, phone number and menu, each when available.
- **FR-013**: The system MUST also be able to return all restaurants of the search zone, beyond the deck, for the results screen (feature 005).
- **FR-014**: The system MUST keep the understood criteria for the session, so that features 003 (refinement) and 005 (pre-selected filters) can use them.
- **FR-015**: When the sentence names several places, the system MUST search around each of them and merge the results into one set, without duplicates, ranked by relevance.
- **FR-016**: The system MUST NOT return for the deck a restaurant with no known address.

### Key Entities *(include if feature involves data)*

- **Search request**: the user's sentence and, if used, the device position.
- **Search criteria**: cuisine types, diets, place, moment, distance limit, and how they are combined (AND/OR).
- **Restaurant**: name, location, distance from the search place, cuisine type, diets satisfied, keywords, price level, weekly opening hours, photo, signature dish, address, phone number, menu (dishes with description and price).
- **Search result**: the ranked restaurants for the deck and the full list of restaurants in the zone.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Time between pressing "Commencer" and the first card being displayed: as fast as possible. The maximum threshold (seconds, percentile) MUST be set by the team at `/speckit-plan`; until then this criterion is not testable.
- **SC-002**: 100% of returned restaurants are within the radius and open at the targeted moment, on the reference sentences.
- **SC-003**: The criteria understood from the reference sentences match the expected criteria in at least a target rate of cases. The target rate, the size of the reference set and who writes it MUST be set by the team at `/speckit-plan`; until then this criterion is not testable.
- **SC-004**: Every failure case of User Story 3 shows its message and action in acceptance testing.

## Assumptions

- **Technical choices are open**: the language model and the restaurant data source are NOT chosen. The developer who takes this feature MUST propose them at `/speckit-plan` and get them validated by the team. The source document mentions an "OpenAlacarte" API as a candidate.
- The data source may not provide every field of FR-012 for every restaurant; features 003 to 006 define what is shown when a field is missing.
- The backend does not exist yet in the repository; a setup chore is needed before implementation.
- Privacy: the user's sentence and position may be sent to third-party services; this MUST be checked at planning time.
- "Moment" may target any coming day (for example "samedi midi"); confirmed by the product owner on 2026-11-24.
- Coverage is France; using Nappe abroad is a later evolution.
- The deck size and the radius are configuration values, not user settings.
