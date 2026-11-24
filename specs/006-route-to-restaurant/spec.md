# Feature Specification: Route to Restaurant

**Feature Branch**: `feat/route-to-restaurant`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: MVP "Philipe, 38, employee and tourist in Paris, wants to find a restaurant matching his tastes and diet: visualize several restaurants, select one, and get a route to it." Spec 4 mockup: route card "Itinéraire depuis votre position" with travel modes, "Lancer l'itinéraire" and "Appeler".

**Feature set**: this spec is 6 of 6 for the MVP: 001 home search screen → 002 natural-language search → 003 swipe deck → 004 restaurant card details → 005 results map screen → 006 route to restaurant.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See how to get to the chosen restaurant (Priority: P1)

Philipe has chosen a restaurant. He sees its key information and the travel time from his position on foot, by bike, by car and by public transport, with the route drawn on a map.

**Why this priority**: "get a route to the selected restaurant" closes the MVP journey.

**Independent Test**: open the route screen for a known restaurant and a known starting point, and check the information, the travel times and the route drawn for each mode.

**Acceptance Scenarios**:

1. **Given** the user picked a restaurant in the deck (feature 003), **Then** the route screen opens for it; **Given** the user selected a restaurant on the results screen (feature 005), **Then** its route card opens next to the map; **Given** the user pressed the full-route button of the card preview (feature 004), **Then** the route screen opens for it.
2. **Given** the route screen is open, **Then** it shows: the restaurant name, cuisine type, distance, price level, open/closed status (for example "Ouvert"), diet tags (for example "100 % végétarien"), today's opening hours ("Aujourd'hui") and the address ("Adresse").
3. **Given** the route screen is open, **Then** under "Itinéraire depuis votre position" it shows four travel modes — "À pied", "Vélo", "Voiture", "Transports en commun" — each with its travel time.
4. **Given** a travel mode is selected, **Then** the route for that mode is drawn on the map and the start ("Départ : votre position, <place>") and arrival ("Arrivée : <restaurant>, <address>") are shown.
5. **Given** the route screen is used on desktop or on mobile, **Then** it is usable on both, with a layout adapted to each device.

---

### User Story 2 - Go there (Priority: P1)

Philipe presses "Lancer l'itinéraire" to follow the route in Nappe, or opens it in another maps application.

**Why this priority**: it turns the choice into an actual visit.

**Independent Test**: press "Lancer l'itinéraire" and the "open in another app" option, and check what happens for each.

**Acceptance Scenarios**:

1. **Given** a travel mode is selected, **When** the user presses "Lancer l'itinéraire", **Then** live guidance starts in Nappe: the user's position is tracked as they move, and the map and the current step are updated until arrival.
2. **Given** the route screen is open, **When** the user chooses to open the route in another maps application, **Then** that application opens with the same destination and travel mode. The applications offered are Google Maps, Apple Plans, Waze and Citymapper; Apple Plans is offered only on Apple devices.

---

### User Story 3 - Call the restaurant (Priority: P2)

Philipe wants to book or check something: he presses "Appeler".

**Why this priority**: useful but not needed to reach the restaurant.

**Independent Test**: on a restaurant with and without a phone number, check the button.

**Acceptance Scenarios**:

1. **Given** the restaurant has a phone number, **When** the user presses "Appeler", **Then** a call to that number is started (or offered, on a device that cannot call).
2. **Given** the restaurant has no phone number, **Then** the "Appeler" button is not displayed.

---

### User Story 4 - Go back (Priority: P3)

After a pick, Philipe wants to compare again; after a preview, he wants to keep swiping.

**Independent Test**: open the route screen from each entry point and check the way back.

**Acceptance Scenarios**:

1. **Given** the route screen was opened by a pick in the deck, **Then** a button opens the results screen (feature 005), and the user can also go back to the deck, which goes on at the next card with the picked restaurant counted as liked (feature 003).
2. **Given** the route screen was opened from the card preview (feature 004), **When** the user goes back, **Then** they return to the deck at the same card with reactions kept.

---

### Edge Cases

- The user's position is not known (device position refused; the search place came from the sentence): the device position is requested again when the route is shown; if it is refused again or unavailable, the route starts from the search place, and the start is labelled with that place instead of "votre position" (see FR-010).
- A travel mode has no possible route (for example no public transport at that time): that mode is hidden (see FR-011).
- Live guidance needs the user's position: if the position is refused or lost, guidance does not start (or pauses if it was running), the message "Activez votre position pour être guidé jusqu'au restaurant." is shown, and the route stays visible on the map.
- The restaurant is closed at the moment: its status shows it; the route is still available.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The route screen MUST open from a pick in the deck, from the full-route button of the card preview, and as a route card when a restaurant is selected on the results screen.
- **FR-002**: The route screen MUST show the restaurant information listed in User Story 1, scenario 2.
- **FR-003**: The route screen MUST show the travel time on foot, by bike, by car and by public transport, from the user's position.
- **FR-004**: The selected travel mode's route MUST be drawn on a map, with start and arrival.
- **FR-005**: "Lancer l'itinéraire" MUST start live guidance inside Nappe: the user's position is tracked and the map and current step are updated as they move, until arrival.
- **FR-006**: The user MUST be able to open the route in another maps application, chosen from this list: Google Maps, Apple Plans, Waze, Citymapper. Apple Plans MUST be offered only on Apple devices.
- **FR-007**: "Appeler" MUST start or offer a call to the restaurant, and MUST NOT be displayed when no phone number is known.
- **FR-008**: After a pick, the route screen MUST offer a button to the results screen and a way back to the deck (next card, pick counted as liked); after a preview, going back MUST return to the deck at the same card.
- **FR-009**: The route screen MUST be responsive (desktop and mobile).
- **FR-010**: The route MUST start from the user's position. When it is not known, the system MUST request it again when the route is shown; if it is refused again or unavailable, the route MUST start from the search place, labelled with that place instead of "votre position".
- **FR-011**: A travel mode with no possible route MUST be hidden, on the route screen and in the route preview of feature 004.

### Key Entities *(include if feature involves data)*

- **Route**: start point, arrival (restaurant address), travel mode, travel time, path.
- **Travel mode**: on foot, bike, car, public transport.
- **Restaurant**: as defined in feature 002.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing, 100% of route screens show a travel time for each available mode.
- **SC-002**: In acceptance testing, the route opened in another maps application has the same destination as in Nappe in 100% of cases.
- **SC-003**: At least 90% of test users start a route to their chosen restaurant without help. (Target validated by the product owner on 2026-11-24.)

## Assumptions

- The mockup (Spec 4 page of the source PDF) is the visual reference; "Grenoble centre" and "[ADRESSE]" are filled with real data.
- The routing and map services are chosen at planning time; the map should be open source, customizable and without an API key if possible (same constraint as feature 005). Public transport coverage across France depends on the chosen service.
- The route computation is shared with the route preview of feature 004.
- Privacy: the user's position may be sent to a routing service; to be checked at planning time.
