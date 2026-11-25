# Feature Specification: Swipe Deck

**Feature Branch**: `feat/swipe-deck`

**Created**: 2026-11-24

**Status**: Approved (product owner, 2026-11-24); amendment of 2026-11-25 (US1 AS6–AS8, FR-015–FR-018: tablecloth under the stack, no card behind, "En cuisine…" crêpe toss) approved (product owner, 2026-11-25); amendment of 2026-11-25 (b) (FR-019–FR-021: opening the deck from the home search) pending approval

**Input**: User description: "Spec 3.1 – Tinder-like front. The search proposes restaurant ideas in a 'Tinder' way. The LLM adapts to the swipes to propose other restaurants and refine the search. As a user, I want my reactions to refine the next cards so that the proposals match my tastes. Swipe left = no, swipe right = yes, swipe up = we take this one. The card shows the distance, today's opening hours, the restaurant name and a few relevant keywords." MVP persona: "Philipe, 38, employee and tourist in Paris, wants to find a restaurant matching his tastes and diet."

**Feature set**: this spec is 3 of 6 for the MVP: 001 home search screen → 002 natural-language search → 003 swipe deck → 004 restaurant card details → 005 results map screen → 006 route to restaurant.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse the proposed restaurants one card at a time (Priority: P1)

After his search, Philipe sees the proposed restaurants as a deck of cards, one at a time, under a title that adapts to the targeted moment (for example "Ça vous dit, ce soir ?"), with a progress indicator.

**Why this priority**: it is how the search results are presented; nothing else in the deck works without it.

**Independent Test**: feed the deck with a fixed list of restaurants and check the content of each card and the progress indicator.

**Acceptance Scenarios**:

1. **Given** a search returned restaurants, **When** the deck opens, **Then** the first card is shown with the title "Ça vous dit, <moment> ?" built from the targeted moment (for example "Ça vous dit, ce soir ?", "Ça vous dit, samedi midi ?", "Ça vous dit, maintenant ?" when no moment is given) and the progress "1 sur N", where N is the deck size.
2. **Given** a card is shown, **Then** its front shows: the restaurant photo, the distance, the name, relevant keywords (for example cuisine, "Options végé", "Épicé"), the price level (for example "€€"), the opening status and hours of the targeted day — today by default, or the day of the moment expressed in the sentence (feature 002) — (for example "Ouvert jusqu'à 23:00") and an "À goûter" block naming a dish one can eat there.
3. **Given** a restaurant has no photo, **When** its card is shown, **Then** a default tablecloth visual in the style of the Nappe logo is shown instead.
4. **Given** no dish is known for a restaurant, **When** its card is shown, **Then** the "À goûter" block shows a default dish visual and no dish name.
5. **Given** the deck is opened on desktop or on mobile, **Then** it is usable on both, with a layout adapted to each device.
6. **Given** the deck is shown, **Then** the area under the card stack shows a discreet lavender gingham tablecloth in the style of the Nappe logo, so that the cards look laid on a table; no other card is shown behind the current card (amended 2026-11-25).
7. **Given** the current card is moved away (dragged, or flying off after a reaction) or the next card is being prepared, **Then** an animated scene is visible on the tablecloth under the card: a crêpe tossed in the air from a pan and caught again, with the text "En cuisine…"; when the user asks the system to reduce motion, only the text "En cuisine…" is shown (amended 2026-11-25).
8. **Given** the user reacted to a card, **When** the next card is prepared, **Then** it is shown only after at least one full crêpe toss, even if the refinement answered earlier; no minimum wait applies when the user asks the system to reduce motion (amended 2026-11-25).

---

### User Story 2 - React to each card (Priority: P1)

Philipe says no, yes, or "this is the one" to each card, by gesture or with buttons.

**Why this priority**: reactions are the purpose of the deck and feed both refinement and the results screen.

**Independent Test**: on a fixed deck, perform each reaction by gesture and by button and check the recorded reaction and the next screen.

**Acceptance Scenarios**:

1. **Given** a card is shown, **When** the user swipes left or presses "✕", **Then** the restaurant is recorded as disliked and the next card is shown.
2. **Given** a card is shown, **When** the user swipes right or presses "♥", **Then** the restaurant is recorded as liked and the next card is shown.
3. **Given** a card is shown, **When** the user swipes up or presses "→", **Then** the restaurant is recorded as picked and the route screen (feature 006) opens for it, with a button to the results screen (feature 005).
4. **Given** the deck is used on a touch screen, **Then** swipes are done with the finger; **Given** it is used with a mouse, **Then** cards can be dragged with the mouse and the three buttons are available.
5. **Given** the deck is in progress, **When** the user presses "Voir la liste", **Then** the results screen (feature 005) opens with the reactions recorded so far.

---

### User Story 3 - Get better proposals as I react (Priority: P2)

After each reaction, the cards still to come are recomputed from Philipe's reactions so that they match his tastes better.

**Why this priority**: it is the differentiating value of Nappe, but the deck is already useful without it.

**Independent Test**: with a controlled set of restaurants, like several restaurants of one cuisine and dislike another cuisine, and check that the following cards favor the liked cuisine.

**Acceptance Scenarios**:

1. **Given** the user reacted to a card, **When** the next card is shown, **Then** the remaining cards have been recomputed taking all reactions of the deck into account.
2. **Given** refinement, **Then** the deck size stays the same (15 by default) and the progress indicator keeps the same total.
3. **Given** refinement, **Then** a restaurant already shown in this deck is never shown again, except through rewind.
4. **Given** refinement, **Then** a restaurant closed at the targeted moment is never proposed (rule of feature 002).

---

### User Story 4 - Undo a mistake (Priority: P2)

Philipe swiped too fast; he presses "<" to go back and change his reaction.

**Why this priority**: it avoids frustration but is not needed for the main flow.

**Independent Test**: react to several cards, press "<" repeatedly and check that cards come back in reverse order with their reactions removed.

**Acceptance Scenarios**:

1. **Given** the user reacted to at least one card, **When** they press "<", **Then** the previous card is shown again and its reaction is cancelled.
2. **Given** the user rewound several times, **When** they press "<" again, **Then** they can go back as far as the first card of the deck.
3. **Given** the first card is shown, **Then** "<" does nothing or is disabled.
4. **Given** a reaction was cancelled, **When** the following cards are shown, **Then** they are recomputed without the cancelled reaction.

---

### User Story 5 - Reach the end of the deck (Priority: P3)

When Philipe has reacted to every card, he lands on the results screen.

**Why this priority**: it closes the flow; the results screen itself is feature 005.

**Independent Test**: react to all cards of a deck and check the next screen.

**Acceptance Scenarios**:

1. **Given** the last card of the deck, **When** the user likes or dislikes it, **Then** the results screen (feature 005) opens.
2. **Given** the search returned fewer restaurants than the deck size, **Then** the deck contains only those and the progress total is that number.

---

### Edge Cases

- Refinement cannot be computed (service unavailable) during the deck: the deck goes on with the cards already planned, without refinement, and a discreet message tells the user that suggestions are not personalized for now (see FR-013). Refinement resumes at the next reaction if the service is back.
- The user picks a restaurant (swipe up), then changes their mind: they can return to the deck from the route screen; the picked restaurant stays recorded as liked, and the deck goes on at the next card (see FR-014).
- The user refreshes the page or closes the tab: the deck and reactions are lost (reactions are kept for the session only).
- The user dislikes every card: the deck ends and the results screen opens.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST show the search results as a deck of cards, one at a time, with the title "Ça vous dit, <moment> ?" adapted to the targeted moment (feature 002), and a progress indicator "X sur N".
- **FR-002**: The deck size MUST be the configured value (15 by default), or fewer when fewer restaurants match.
- **FR-003**: Each card front MUST show the content listed in User Story 1, scenario 2, with the fallbacks of scenarios 3 and 4.
- **FR-004**: Users MUST be able to dislike (swipe left or "✕"), like (swipe right or "♥") and pick (swipe up or "→") the current card.
- **FR-005**: Gestures MUST work by touch on mobile and by mouse drag on desktop; the three buttons MUST be available on both.
- **FR-006**: After each reaction, the system MUST recompute the remaining cards from all reactions of the deck, keep the same deck size, and never show again a restaurant already shown in the deck except through rewind.
- **FR-007**: Users MUST be able to rewind with "<", repeatedly, back to the first card; a rewound reaction is cancelled and the following cards are recomputed without it.
- **FR-008**: A pick MUST open the route screen (feature 006) for the picked restaurant, with a button to the results screen (feature 005).
- **FR-009**: "Voir la liste" MUST open the results screen (feature 005) at any time during the deck.
- **FR-010**: At the end of the deck, the system MUST open the results screen (feature 005).
- **FR-011**: Reactions (liked, disliked, picked) MUST be kept for the session only and MUST be available to feature 005.
- **FR-012**: The "Vos coups de cœur" side panel shown on the mockup MUST NOT be displayed.
- **FR-013**: When refinement fails, the deck MUST go on with the cards already planned, without blocking the user, and MUST show a discreet, non-blocking message with the wording "Suggestions non personnalisées pour le moment"; refinement MUST resume at the next reaction once the service is available.
- **FR-014**: After a pick, the user MUST be able to return from the route screen to the deck; the picked restaurant MUST then count as liked, and the deck MUST go on at the next card with all reactions kept.
- **FR-015**: The area under the card stack MUST show a discreet lavender gingham tablecloth (Nappe logo style), starting below the title; no other card is shown behind the current card (amended 2026-11-25).
- **FR-016**: The next card MUST NOT be shown before the refinement that follows a reaction has answered or failed; no refinement is computed in advance (amended 2026-11-25).
- **FR-017**: Under the current card, on the tablecloth, the deck MUST show a crêpe tossed from a pan with the text "En cuisine…", visible as soon as the card is moved away and while the next card is prepared; with reduced motion, only the text (amended 2026-11-25).
- **FR-018**: After a reaction, the next card MUST NOT be shown before one full crêpe toss has played (1 s), even when the refinement answered earlier; no minimum wait with reduced motion (amended 2026-11-25).
- **FR-019**: When the search started from the home screen (feature 001) succeeds, the system MUST open the deck built from that search's result (amended 2026-11-25 (b)).
- **FR-020**: A new search from the home screen MUST start a new deck from its own result; the previous deck and its reactions are discarded (amended 2026-11-25 (b)).
- **FR-021**: Opening the deck when no search was made in the session MUST open the home screen instead (amended 2026-11-25 (b)).

### Key Entities *(include if feature involves data)*

- **Deck**: the ordered set of restaurants proposed for one search, its size, and the position of the current card.
- **Reaction**: liked, disliked or picked, linked to one restaurant of the deck; can be cancelled by rewind.
- **Restaurant**: as defined in feature 002.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Time between a reaction and the next card being shown, refinement included: as fast as possible. The maximum threshold MUST be set by the team at `/speckit-plan`; until then this criterion is not testable.
- **SC-002**: In acceptance testing, 100% of reactions done by gesture and by button are recorded as the expected reaction.
- **SC-003**: In acceptance testing, 0 restaurant appears twice in the same deck outside rewind.
- **SC-004**: After liking restaurants of one cuisine, the share of that cuisine in the following cards increases, on a controlled test set defined at planning time.

### Test plan — amendment 2026-11-25 (b)

The Expected column is written by the product owner only.

| Test | Rule | Case | Input | Expected |
|---|---|---|---|---|
| T1 | FR-019 | nominal | Home screen, sentence "italien ce soir près d'Opéra", press "Commencer", the search succeeds | On arrive sur deck on voit les cartes en fonction du resultat du llm |
| T2 | FR-019 | limit: search in progress | Same sentence, before the search has answered | On est sur home screen on voit un chargement |
| T3 | FR-020 | nominal | Deck at card 3 with 2 reactions, back to home, new sentence, search succeeds | On arrive sur deck on voit le nouveau resultat |
| T4 | FR-021 | limit: no search | Open `/deck` directly in a new session | On revient sur homescreen |
| T5 | FR-019 | limit: search fails | Sentence submitted, the search returns an error | On reste sur home screen on voit un message d'erreur |

## Assumptions

- The title follows the mockup pattern "Ça vous dit, ce soir ?" and adapts to the targeted moment; confirmed by the product owner on 2026-11-24.
- The mockup (Spec 3.1 page of the source PDF) is the visual reference, with "Nappe" in place of "Tablée".
- The default tablecloth visual and the default dish visual are to be designed.
- The card back (flip, details, route preview) is feature 004.
- Refinement relies on the language model chosen in feature 002.
- Translation, accounts and saving reactions beyond the session are out of scope.
