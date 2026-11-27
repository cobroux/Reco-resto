# Feature Specification: Results Map Screen

**Feature Branch**: `feat/results-map-screen`

**Created**: 2026-11-24

**Status**: Draft, implemented on this branch; awaiting teammate approval of the spec (the implementation was written before the approval, the Spec Kit order specify → plan → tasks → implement was not followed, see the PR description)

**Input**: User description: "Spec 4 – End-of-swipe screen proposing the restaurants. At the end of the swipes, show a screen that displays our favorite results, places them on a map, and offers a filter on the preference type, or all the restaurants in the zone (even the refused ones, but greyed out)." MVP persona: "Philipe, 38, employee and tourist in Paris, wants to find a restaurant matching his tastes and diet; visualize several restaurants, select one, and get a route to it."

**Feature set**: this spec is 5 of 6 for the MVP: 001 home search screen → 002 natural-language search → 003 swipe deck → 004 restaurant card details → 005 results map screen → 006 route to restaurant.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See my favorites and all restaurants around (Priority: P1)

After the deck, Philipe lands on "Autour de vous". The "Ma recherche" tab lists the restaurants he liked; the "Tous" tab lists every restaurant of the search zone, his likes first, the ones he refused greyed out.

**Why this priority**: it is where the user compares restaurants before choosing one; it fulfils "visualize several restaurants" of the MVP.

**Independent Test**: with a fixed set of reactions (likes, dislikes, never shown), open the screen and check the content and order of both tabs.

**Acceptance Scenarios**:

1. **Given** the user reached the end of the deck, **Then** the results screen opens; **Given** the user pressed "Voir la liste" during the deck or the results button of the route screen, **Then** the results screen opens too.
2. **Given** the screen is open, **Then** it shows the name of the searched area (for example "Grenoble centre" on the mockup) and the title "Autour de vous".
3. **Given** the "Ma recherche" tab is selected, **Then** it lists only the liked restaurants, ordered by relevance, and the filters are hidden (all likes are always shown).
4. **Given** the "Tous" tab is selected, **Then** it lists every restaurant of the search zone: liked restaurants first, then all others ordered by relevance, including restaurants never shown in the deck.
5. **Given** the "Tous" tab is selected, **Then** disliked restaurants are shown greyed out.
6. **Given** a restaurant is closed at the moment, **Then** it is shown with its opening mention (for example "Ferme à 14:30").
7. **Given** a restaurant is listed, **Then** its card shows the photo (or the default tablecloth visual), the distance, the name, the cuisine type and its open/closed status.

---

### User Story 2 - See the restaurants on a map and select one (Priority: P1)

Philipe sees the restaurants as points on a map, with his position, and selects one from the map or from the list.

**Why this priority**: "place them on a map" and "select a restaurant" are explicit MVP needs.

**Independent Test**: open the screen with a known set of restaurants, check the points on the map, click a point and a list card, and check the selection.

**Acceptance Scenarios**:

1. **Given** the screen is open, **Then** a map shows the user's position and one point per restaurant of the current tab and filters.
2. **Given** a restaurant is disliked, **Then** its point on the map is greyed out, as in the list.
3. **Given** the map is shown, **When** the user clicks a point, **Then** the restaurant is selected, highlighted in the list, and its route card (feature 006) opens.
4. **Given** the list is shown, **When** the user clicks a restaurant card, **Then** the restaurant is selected, highlighted on the map, and its route card (feature 006) opens.

---

### User Story 3 - Filter the restaurants (Priority: P2)

Philipe narrows the list with dietary filters and with cuisine type, distance and "open now" filters.

**Why this priority**: it refines the choice but the screen is useful without it.

**Independent Test**: on a known set of restaurants, combine filters and check the list and the map.

**Acceptance Scenarios**:

1. **Given** the screen is open, **Then** dietary filters are shown: "Végétarien", "Vegan", "Sans gluten", "Halal", "Casher", "Sans lactose".
2. **Given** the search sentence contained diets, **When** the screen opens, **Then** those dietary filters are pre-selected.
3. **Given** several dietary filters are selected, **Then** a restaurant is shown if it satisfies at least one of them (OR).
4. **Given** the screen is open, **Then** the filters "Type" (cuisine, as a drop-down list), "Moins de 1 km" and "Ouvert maintenant" are shown.
5. **Given** filters of different categories are selected (diet, type, distance, open now), **Then** a restaurant is shown only if it satisfies every category (AND between categories).
6. **Given** a filter is selected or removed, **Then** the list and the map are updated without retyping the search.
7. **Given** the filters match no restaurant, **Then** a message says that no restaurant matches these filters and suggests which filter to remove to get results, with the number of restaurants it would bring back with the wording "Retirez « <filtre> » pour voir <N> restaurants" (for example "Retirez « Moins de 1 km » pour voir 4 restaurants").
8. **Given** the suggestion is shown, **When** the user follows it, **Then** that filter is removed and the list and the map are updated.

---

### User Story 4 - Start again (Priority: P3)

Philipe is not convinced: he restarts a deck on the same search, or starts a new search.

**Why this priority**: useful loop, not needed for the first choice.

**Independent Test**: press each button and check the screen reached.

**Acceptance Scenarios**:

1. **Given** the screen is open, **When** the user presses "Relancer le swipe", **Then** a new deck starts on the same search, without any restaurant already shown in the session, refined with the previous reactions, and the previous likes stay in "Ma recherche".
2. **Given** the screen is open, **When** the user presses "Nouvelle recherche", **Then** the home screen (feature 001) opens.

---

### Edge Cases

- The user liked no restaurant: the "Ma recherche" tab shows the message "Aucun coup de cœur pour l'instant" with two buttons, "Voir tous les restaurants" (opens the "Tous" tab) and "Relancer le swipe" (see FR-016).
- The user's position is not known (refused): the map is centred on the searched place; whether a user marker is shown is decided with feature 006.
- A new deck started with "Relancer le swipe": it excludes every restaurant already shown in previous decks of the session, it is refined with all previous reactions, and previous likes are kept, so "Ma recherche" accumulates the likes of all decks (see FR-017).
- No restaurant of the zone is left to show for a new deck: the message "Vous avez vu tous les restaurants autour de vous." is shown with a "Nouvelle recherche" button, and no new deck starts.
- The screen is opened on mobile: list and map are both reachable, with a layout adapted to the small screen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The results screen MUST open at the end of the deck, from "Voir la liste" during the deck, and from the results button of the route screen.
- **FR-002**: The screen MUST show the searched area name and the title "Autour de vous".
- **FR-003**: The "Ma recherche" tab MUST list only the liked restaurants, ordered by relevance.
- **FR-004**: The "Tous" tab MUST list all restaurants of the search zone, liked first, then the others by relevance, disliked ones greyed out.
- **FR-005**: Restaurants closed at the moment MUST be listed with their opening mention.
- **FR-006**: The screen MUST show a map with the user's position and the restaurants of the current tab and filters; disliked restaurants MUST be greyed out on the map.
- **FR-007**: Selecting a restaurant on the map or in the list MUST highlight it in both and open its route card (feature 006).
- **FR-008**: The screen MUST offer the dietary filters "Végétarien", "Vegan", "Sans gluten", "Halal", "Casher", "Sans lactose"; this list MUST be extensible without changing the filtering rules.
- **FR-009**: Diets understood in the search sentence MUST be pre-selected.
- **FR-010**: Dietary filters MUST combine with OR; filter categories (diet, type, distance under 1 km, open now) MUST combine with AND.
- **FR-011**: Filters MUST update the list and the map immediately.
- **FR-012**: The screen MUST offer "Relancer le swipe" (new deck on the same search) and "Nouvelle recherche" (back to the home screen).
- **FR-013**: The screen MUST NOT show the search bar "Restaurant, plat, cuisine…" nor the bookmark icon of the mockup.
- **FR-014**: The screen MUST be responsive (desktop and mobile).
- **FR-015**: When the active filters match no restaurant, the screen MUST show a message suggesting which filter to remove to get results, with the number of restaurants it would bring back, and following the suggestion MUST remove that filter.
- **FR-016**: When there is no liked restaurant, the "Ma recherche" tab MUST show a message with the buttons "Voir tous les restaurants" (switches to the "Tous" tab) and "Relancer le swipe".
- **FR-017**: "Relancer le swipe" MUST start a new deck on the same search that excludes restaurants already shown in the session, is refined with all previous reactions, and keeps previous likes and dislikes.
- **FR-018**: Filters MUST apply to the "Tous" tab only and MUST be hidden on the "Ma recherche" tab, which always shows all liked restaurants.
- **FR-019**: Restaurants with no known address MUST NOT be listed in the "Tous" tab nor shown on the map (same rule as the deck, feature 002 FR-016).

### Key Entities *(include if feature involves data)*

- **Results view**: the selected tab, the active filters and the selected restaurant.
- **Filter**: a category (diet, type, distance, open now) and its selected values.
- **Restaurant** and **Reaction**: as defined in features 002 and 003.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing, 100% of disliked restaurants are greyed out in the list and on the map.
- **SC-002**: In acceptance testing, 100% of restaurants shown with active filters satisfy the OR/AND rules of FR-010.
- **SC-003**: Time to update the list and the map after a filter change: as fast as possible; a maximum threshold is to be set by the team.
- **SC-004**: At least 90% of test users select a restaurant from the map or the list without help. (Target validated by the product owner on 2026-11-24.)

## Assumptions

- The mockup (Spec 4 page of the source PDF) is the visual reference, with "Nappe" in place of "Tablée".
- The "search zone" of the "Tous" tab is the search radius of feature 002 (2.5 km by default, or the distance of the sentence), around every place of the search; confirmed by the product owner on 2026-11-24.
- Filters apply to the "Tous" tab only; confirmed by the product owner on 2026-11-24.
- Map provider: the team asks for an open-source, customizable map, without an API key if possible; chosen at planning time.
- Filtering may rely on the language model (product owner's suggestion); decided at planning time.
- The route card shown next to the map is feature 006.
- Walking distances: the distance shown on the deck, the results and the map is the walking distance from the community OSRM service when it answers, and the distance of the backend (straight line) otherwise. **This replaces the backend distance of existing behaviour; accepted on 2026-11-27 by the author of the branch, to be confirmed by the reviewers on the PR.** The deck never waits for the routing service: it opens with the backend distances and takes the walking ones when they arrive, keeping its progress.
- Configuration: the tiles URL (OpenFreeMap), the routing URL (`routing.openstreetmap.de/routed-foot`) and the routing timeout (8 s) live in one injection token (`MAP_CONFIG`, `frontend/src/app/map/map-config.token.ts`). **The 8 s timeout is an unvalidated placeholder: to be confirmed by the team.** Since the deck no longer waits, it only bounds the background call.
- Map failure: when MapLibre cannot be loaded, the map cannot be created (no WebGL) or the style/tiles cannot be fetched before the first display, the map is replaced by the message "La carte n’est pas disponible pour le moment." (proposed wording, to be confirmed). The list and the rest of the screen stay usable. A tile that fails after the first display leaves the map in place.

## Privacy

For the MVP, the browser calls the community OSRM service `routing.openstreetmap.de` (pedestrian profile) directly, on every search:

- it receives the user's exact position together with up to 99 restaurant positions (distance matrix) and, for the selected restaurant, the route from the user to it;
- the host also sees the user's IP address and user agent;
- the vector tiles come from `tiles.openfreemap.org`, which sees the IP address and the viewed area.

**Accepted for the MVP on 2026-11-27 by the author of the branch (to be confirmed by the reviewers on the PR, as for the third parties of feature 002, 002 plan O5).** The service has no guaranteed availability and MUST be replaced before a production release: either proxy the calls through the backend or self-host OSRM. Nothing in the UI tells the user this today; a user-facing privacy notice is required before a production release (no privacy notice exists in the frontend yet; its location is still open).
