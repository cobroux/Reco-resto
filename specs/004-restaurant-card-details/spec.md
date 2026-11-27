# Feature Specification: Restaurant Card Details

**Feature Branch**: `feat/restaurant-card-details`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "Spec 3.2 – Tinder-like back side. We should be able to flip the swipe card to give more information about the restaurant." MVP persona: "Philipe, 38, employee and tourist in Paris, wants to find a restaurant matching his tastes and diet."

**Feature set**: this spec is 4 of 6 for the MVP: 001 home search screen → 002 natural-language search → 003 swipe deck → 004 restaurant card details → 005 results map screen → 006 route to restaurant.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Flip a card to learn more (Priority: P1)

Before deciding, Philipe flips the current card to see the opening hours of the week, the address and the menu.

**Why this priority**: it is the core of this feature; it helps the user decide without leaving the deck.

**Independent Test**: on a card with complete data, flip it and check every section; flip it back and check the front is shown again.

**Acceptance Scenarios**:

1. **Given** a card front is shown, **When** the user presses the flip button "↻" or taps the card, **Then** the card back is shown.
2. **Given** the card back is shown, **When** the user presses "↻" or taps the card, **Then** the card front is shown again.
3. **Given** the card back is shown, **Then** it shows: the restaurant name, cuisine type, distance and price level; the keywords (for example "Options végé", "Épicé"); a "Horaires" section with the opening hours of each day of the week, the targeted day highlighted (today by default, or the day of the moment expressed in the sentence); an "Adresse" section with the address and an "Itinéraire" button; a "Menu" section listing dishes with their description and price.
4. **Given** the card back holds more content than the card height, **Then** the user can scroll inside the card.
5. **Given** the restaurant has no menu, **When** the back is shown, **Then** the "Menu" section is not displayed.
6. **Given** the restaurant has no opening hours, **When** the back is shown, **Then** the "Horaires" section is not displayed.
7. **Given** the card back is used on desktop or on mobile, **Then** it is usable on both, with a layout adapted to each device.

---

### User Story 2 - Preview the route without leaving the deck (Priority: P2)

On the card back, Philipe presses "Itinéraire" to see how long it takes to get there, then keeps swiping.

**Why this priority**: distance and travel time are key for a tourist, but the full route is feature 006.

**Independent Test**: on a card back, press "Itinéraire", check the preview content, then react to the card and check the deck goes on.

**Acceptance Scenarios**:

1. **Given** the card back is shown, **When** the user presses "Itinéraire", **Then** a route preview is shown inside the card, with the travel time from the user's position on foot, by bike, by car and by public transport.
2. **Given** the route preview is shown, **Then** viewing it does not count as a pick: the user can still like, dislike or pick the card.
3. **Given** the route preview is shown, **When** the user presses the button to the full route, **Then** the route screen (feature 006) opens for this restaurant.
4. **Given** the user opened the full route from the preview, **When** they go back, **Then** they return to the deck at the same card, with all reactions kept.

---

### Edge Cases

- A travel mode has no possible route for this restaurant (for example no public transport at that time): that mode is hidden in the preview (same rule as feature 006).
- The user's position is not known (the search place came from the sentence and the device position was refused): the position is requested again when the preview is shown; if refused again, the preview starts from the search place (same rule as feature 006, FR-010).
- The address is missing: this cannot happen in the deck, because restaurants with no known address are excluded from it (feature 002, FR-016).
- The user reacts (like, dislike, pick) while the back is shown: the reaction applies to this restaurant as on the front.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to flip the current card with the "↻" button or by tapping it, in both directions.
- **FR-002**: The card back MUST show the content listed in User Story 1, scenario 3.
- **FR-003**: A section whose data is missing (menu, opening hours) MUST NOT be displayed.
- **FR-004**: The targeted day (today by default, or the day of the moment expressed in the sentence, feature 002) MUST be highlighted in the opening hours of the week.
- **FR-005**: The "Itinéraire" button MUST show a route preview inside the card, with travel times on foot, by bike, by car and by public transport.
- **FR-006**: Viewing the route preview MUST NOT count as a reaction.
- **FR-007**: The route preview MUST offer a button to the full route screen (feature 006); coming back from it MUST return to the deck at the same card with reactions kept.
- **FR-008**: The reaction buttons of the deck (feature 003) MUST stay available while the back is shown, as on the mockup.
- **FR-009**: The card back MUST be responsive (desktop and mobile).

### Key Entities *(include if feature involves data)*

- **Restaurant details**: weekly opening hours, address, menu (dishes with description and price), keywords; defined with the restaurant in feature 002.
- **Route preview**: travel time per mode (on foot, bike, car, public transport) from the user's position to the restaurant.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing, 100% of cards with missing menu or opening hours show no empty section.
- **SC-002**: In acceptance testing, 100% of route previews opened from a card leave the deck position and reactions unchanged.
- **SC-003**: At least 80% of test users find the opening hours of a restaurant without help. (Target validated by the product owner on 2026-11-24.)

## Assumptions

- The mockup (Spec 3.2 page of the source PDF) is the visual reference; the placeholders "[ADRESSE], Grenoble" and "[PRIX]" are filled with real data.
- Travel times reuse the route computation of feature 006; the routing service is chosen at planning time.
- The deck behavior (reactions, refinement, rewind) is feature 003.
