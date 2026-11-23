# Feature Specification: Restaurant Search Bar

**Feature Branch**: `chore/project-setup` (spec created on the current branch; a `feat/restaurant-search-bar` branch is expected before implementation)

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "Search bar. As a user I want to search a restaurant by writing my address. As a user I click according to taste, the best choice possible (example: vegan, gluten free, halal, take away)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find restaurants around an address (Priority: P1)

A user types an address in the search bar and gets a list of restaurants located around that address.

**Why this priority**: It is the entry point of the product. Without it, no restaurant can be recommended. It delivers value on its own, even without dietary filters.

**Independent Test**: Type a valid address, submit, and check that a list of nearby restaurants is displayed. No filter is needed.

**Acceptance Scenarios**:

1. **Given** the search bar is empty, **When** the user types a valid address and submits, **Then** a list of restaurants around that address is displayed.
2. **Given** the user typed an address, **When** no restaurant is found around it, **Then** a clear "no result" message is shown.
3. **Given** the user submits an empty search bar, **When** the search is triggered, **Then** no search is run and the user is asked to enter an address.

---

### User Story 2 - Narrow results by taste and dietary needs (Priority: P2)

A user selects one or more preferences (for example vegan, gluten free, halal, take away) so that the list only shows the restaurants that best match them.

**Why this priority**: It turns a plain list into a personalized recommendation, which is the purpose of Reco-Resto. It needs the results of Story 1 to be useful.

**Independent Test**: With results displayed, click a preference (for example "vegan") and check that only matching restaurants remain; click it again and check that the full list comes back.

**Acceptance Scenarios**:

1. **Given** a list of restaurants is displayed, **When** the user selects one preference, **Then** only restaurants matching it are displayed.
2. **Given** one preference is selected, **When** the user selects a second one, **Then** the list reflects the combination of both (see FR-007).
3. **Given** a preference is selected, **When** the user clicks it again, **Then** it is removed and the list is updated.
4. **Given** the selected preferences match no restaurant, **When** the list is refreshed, **Then** a clear "no result" message is shown and the user can remove preferences.

---

### Edge Cases

- The address is incomplete, misspelled or does not exist: the user sees an explicit message and can correct it.
- The address is ambiguous (several places share the name): the user can choose among the proposals (see Assumptions).
- The address is outside the covered area: the user sees an explicit message.
- The address has leading/trailing spaces or only spaces: handled like an empty input.
- The user changes the address while preferences are selected: the selected preferences are kept (see Assumptions).
- The address service is unavailable: the user sees an error message and can retry; no empty list is shown as if there were no restaurant.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to type an address in a search bar and submit it.
- **FR-002**: System MUST display the restaurants located around the submitted address.
- **FR-003**: System MUST show a clear message when the address is empty, not recognized, ambiguous or outside the covered area, and when no restaurant is found.
- **FR-004**: Users MUST be able to select and deselect preferences by clicking them.
- **FR-005**: System MUST offer at least these preferences: vegan, gluten free, halal, take away. [NEEDS CLARIFICATION: is this list closed to these four, or must other preferences (for example vegetarian, kosher, delivery) be included? Who maintains the list?]
- **FR-006**: System MUST update the displayed list as soon as a preference is selected or deselected, without the user retyping the address.
- **FR-007**: System MUST apply multiple selected preferences together. [NEEDS CLARIFICATION: when several preferences are selected, must a restaurant match ALL of them (e.g. vegan AND halal) or ANY of them?]
- **FR-008**: System MUST show, for each restaurant in the list, which of the offered preferences it matches.
- **FR-009**: System MUST limit the results to restaurants within a given distance of the address. [NEEDS CLARIFICATION: what is the search distance (radius) around the address, and may the user change it?]

### Key Entities *(include if feature involves data)*

- **Search address**: the text typed by the user, and the location it resolves to.
- **Restaurant**: a place to eat, with a name, a location and the preferences it satisfies.
- **Preference**: a taste or dietary need (vegan, gluten free, halal) or a service option (take away) that a restaurant can satisfy and a user can select.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from typing an address to seeing a restaurant list in under 10 seconds, in at least 95% of searches.
- **SC-002**: After a preference is clicked, the updated list is visible in under 1 second, in at least 95% of cases.
- **SC-003**: At least 90% of test users find a restaurant matching one dietary need on their first attempt, without help.
- **SC-004**: Every restaurant shown with a selected preference does match that preference (0 false matches in acceptance testing).

## Assumptions

- Users type a street address (street, number, city); searching by restaurant name or by current device location is out of scope for this version.
- Address suggestions may be proposed while typing, and ambiguous addresses are resolved by letting the user pick one proposal.
- Selected preferences are kept when the address changes.
- Restaurants and their preferences come from a data source that is not defined yet; where it comes from (own database, partner, public data) will be decided at planning time.
- Sorting, ranking, user accounts, favorites, reviews and maps are out of scope for this version.
- The search bar is used on the existing web frontend; mobile-native apps are out of scope.
