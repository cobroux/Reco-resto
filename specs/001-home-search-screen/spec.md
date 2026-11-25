# Feature Specification: Home Search Screen

**Feature Branch**: `feat/home-search-screen`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "Spec 1 – Front search bar. Main screen of the Nappe application, faithful to the mockup text. A search bar connected to an LLM. As a user, I would like to search for a restaurant near my area. The 'Se connecter' button is out of scope." MVP persona: "Philipe, 38, employee and tourist in Paris, wants to find a restaurant matching his tastes and diet."

**Feature set**: this spec is 1 of 6 for the MVP: 001 home search screen → 002 natural-language search → 003 swipe deck → 004 restaurant card details → 005 results map screen → 006 route to restaurant. It replaces the former draft `001-restaurant-search-bar`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Describe what I want to eat and start the search (Priority: P1)

Philipe opens Nappe. He sees the home screen, which presents the product and a single free-text field. He types in his own words what he feels like eating and where (for example "J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris"), then presses "Commencer". His sentence is handed to the search (feature 002).

**Why this priority**: it is the entry point of the whole product. No other screen can be reached without it.

**Independent Test**: open the application, type a sentence, press "Commencer", and check that the exact sentence is submitted to the search step. No result display is needed to test this story.

**Acceptance Scenarios**:

1. **Given** the home screen is displayed, **When** the user types a sentence and presses "Commencer", **Then** the sentence is submitted to the search, unchanged.
2. **Given** the home screen is displayed, **When** the user types a sentence and presses the Enter key, **Then** the sentence is submitted exactly as with "Commencer".
3. **Given** the field is empty or contains only spaces, **When** the user presses "Commencer", **Then** no search is submitted and the message "Votre recherche est vide. Ex. : asiatique végétarien près d'Opéra" is shown.
4. **Given** a search has been submitted, **When** the search is in progress, **Then** the user sees that it is in progress and cannot submit the same sentence twice.

---

### User Story 2 - Understand what Nappe does at first glance (Priority: P2)

Philipe, who does not know the application, understands in a few seconds what Nappe offers (restaurants nearby, adapted to his diet and cravings) through the title, the subtitle and the dietary tiles.

**Why this priority**: it gives context and trust, but the search works without it.

**Independent Test**: display the home screen on a desktop and on a mobile screen, and compare every visible text and element with the mockup (Spec 1 page of the source PDF).

**Acceptance Scenarios**:

1. **Given** the home screen is displayed, **Then** it shows exactly these texts: the "Nappe" logo, the badge "Restaurants autour de vous", the title "Où manger, juste à côté ?", the subtitle "Trouvez un restaurant adapté à votre régime et à vos envies, en quelques secondes.", the field placeholder "Dites-nous ce qui vous ferait plaisir, et où" and the button "Commencer".
2. **Given** the home screen is displayed, **Then** it shows four dietary tiles labelled "Vegan", "Végétarien", "Sans gluten" and "Halal, casher…".
3. **Given** the home screen is displayed, **When** the user clicks or taps a dietary tile, **Then** nothing happens (the tiles are decorative).
4. **Given** the home screen is displayed, **Then** no "Se connecter" button is shown.
5. **Given** the home screen is opened on a desktop screen or on a mobile screen, **Then** all elements are readable and usable without horizontal scrolling, with a layout adapted to each device.

---

### Edge Cases

- The sentence is very long: the field accepts it; the maximum length is decided at planning time with feature 002.
- The sentence contains no place: it is submitted as is; the location fallback is handled by feature 002.
- The sentence is not about food at all: it is submitted as is; feature 002 asks the user to rephrase.
- The user goes back to the home screen after a search: the field is empty, as on first opening.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The home screen MUST display the texts listed in User Story 2, scenario 1, word for word, with the application name "Nappe" in place of "Tablée" shown on the mockup.
- **FR-002**: The home screen MUST offer one free-text field where the user writes, in natural language, what they want to eat and where.
- **FR-003**: Users MUST be able to submit the sentence with the "Commencer" button or with the Enter key.
- **FR-004**: The system MUST NOT submit an empty sentence or a sentence made only of spaces, and MUST show the message "Votre recherche est vide. Ex. : asiatique végétarien près d'Opéra".
- **FR-005**: The system MUST hand the submitted sentence, unchanged, to the natural-language search (feature 002).
- **FR-006**: The system MUST show that a search is in progress and MUST prevent a second submission while it runs.
- **FR-007**: The home screen MUST display the four dietary tiles "Vegan", "Végétarien", "Sans gluten" and "Halal, casher…" as non-interactive decoration.
- **FR-008**: The home screen MUST NOT display a "Se connecter" button.
- **FR-009**: The home screen MUST be responsive: usable on desktop and on mobile, with a layout adapted to each.
- **FR-010**: The interface MUST be in French.

### Key Entities *(include if feature involves data)*

- **Search sentence**: the free text typed by the user; it is the only input of the home screen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the texts listed in FR-001 and FR-007 match the mockup word for word (except the application name).
- **SC-002**: The home screen is fully usable, without horizontal scrolling, on at least one common desktop width and one common mobile width chosen at planning time.
- **SC-003**: 0 empty or space-only sentence reaches the search in acceptance testing.
- **SC-004**: At least 90% of test users, with no help, type a sentence and start a search on their first visit. (Target validated by the product owner on 2026-11-24.)

## Assumptions

- The mockup (Spec 1 page of the source PDF) is the visual and textual reference; only the application name changes ("Tablée" → "Nappe").
- The placeholder "Dites-nous ce qui vous ferait plaisir, et où" was chosen by the product owner; it replaces the mockup's "Votre adresse ou ville" because the field takes a free sentence, not only an address.
- Sign-in ("Se connecter") is out of scope and not developed.
- Translation of the interface into other languages is out of scope; it may be a later feature.
- Coverage is France; using Nappe abroad is a later evolution.
- The interpretation of the sentence, the location fallback (device position), the search itself and its error messages belong to feature 002.
