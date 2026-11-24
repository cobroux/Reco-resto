# Tasks: Swipe Deck

**Input**: Design documents from `/specs/003-swipe-deck/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Required. Constitution II (Test-First): every test task comes before the code it covers and MUST fail before that code is written. Tests live next to the code as `*.spec.ts` (Vitest + jsdom via `npm --prefix frontend test -- --watch=false`).

**Organization**: Tasks are grouped by user story. Decisions D1–D17 are in plan.md, "Decisions on open points".

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: user story from spec.md (US1–US5)
- Paths are relative to the repository root; `app/` means `frontend/src/app/`.

---

## Phase 1: Setup (shared shell, identical to 001 — D9)

**Purpose**: replace the Angular placeholder with the same shell, tokens and font as feature 001 (`origin/feat/home-search-screen`), so the merge conflict has identical content.

- [x] T001 Check that `frontend/node_modules` exists in this worktree; if not, ask the user before running `npm --prefix frontend install` (no install without approval).
- [x] T002 [P] Copy `frontend/src/index.html` from `origin/feat/home-search-screen` verbatim (`lang="fr"`, title "Nappe", Outfit preconnect + stylesheet links).
- [x] T003 [P] Copy `frontend/src/styles.css` from `origin/feat/home-search-screen` verbatim (tokens `--color-page`, `--color-panel`, `--color-primary`, `--color-text`, `--color-muted`, `--color-placeholder`, `--color-white`, `--font-sans`, reset, body). Do not add deck-only colours here: they go in the deck component CSS.
- [x] T004 Copy `app/app.spec.ts` from `origin/feat/home-search-screen` verbatim (shell only hosts a router outlet); run it and see it fail.
- [x] T005 Copy `app/app.ts` and `app/app.html` (`<router-outlet />`) from `origin/feat/home-search-screen` verbatim and delete `app/app.css`; T004 passes.

**Checkpoint**: `npm --prefix frontend test -- --watch=false` passes with the empty shell.

---

## Phase 2: Foundational (contracts, model, stubs, session)

**Purpose**: types and injectable contracts used by every story (contracts/service-contracts.md). No user story starts before this phase is done.

- [x] T006 [P] Create the Restaurant view model and `OpeningRange` in `app/restaurant/restaurant.ts` per data-model.md (`Restaurant.id`, `Restaurant.name`, `Restaurant.distanceMeters`, `Restaurant.cuisine`, `Restaurant.keywords`, optional `Restaurant.priceLevel`, `Restaurant.weeklyHours`, optional `Restaurant.photoUrl`, optional `Restaurant.dish`).
- [x] T007 [P] Create `Reaction` type (data-model.md `Reaction.restaurantId`, `Reaction.kind` = `'liked' | 'disliked' | 'picked'`) and `DeckInput` type (data-model.md `DeckInput.momentLabel` nullable, `DeckInput.targetDateTime` ISO string, `DeckInput.restaurants`) in `app/deck/deck.types.ts`.
- [x] T008 [P] Create abstract `DeckSource` in `app/deck/deck-source.ts` per contracts/service-contracts.md.
- [x] T009 [P] Create `RefinementRequest` and abstract `RefinementService` in `app/deck/refinement.service.ts` per contracts/service-contracts.md.
- [x] T010 [P] Create `DECK_SIZE` injection token (default 15, D17) in `app/deck/deck-size.token.ts`.
- [x] T011 [P] Create `app/deck/deck.texts.ts`, single source of every French text of the deck and placeholders: title pattern "Ça vous dit, {moment} ?", default moment "maintenant", "{position} sur {size}", "Voir la liste", "Pas envie", "Miam", "On y va", "Revenir à la carte précédente" (D4), "À goûter", "Ouvert jusqu'à {time}", "Suggestions non personnalisées pour le moment" (FR-013), "Résultats — bientôt disponible", "Retour aux propositions" (D13).
- [x] T012 [P] Write `app/deck/stub/stub-restaurants.spec.ts`: data set has 16 restaurants (D6), unique ids, at least 3 cuisines, at least one without `photoUrl`, at least one without `dish`, every restaurant open at the stub `targetDateTime`.
- [x] T013 Create `app/deck/stub/stub-restaurants.ts`: 16 fictional Paris restaurants and the stub `targetDateTime` / `momentLabel: 'ce soir'` (D6); T012 passes.
- [x] T014 [P] Write `app/deck/stub/stub-deck-source.spec.ts`: `load()` emits the stub `DeckInput`.
- [x] T015 Implement `app/deck/stub/stub-deck-source.ts`; T014 passes.
- [x] T016 [P] Write `app/deck/stub/stub-refinement.service.spec.ts`: returns candidates not in `excludedIds`, initial order, truncated to `slots`; failure mode makes `refine` error.
- [x] T017 Implement `app/deck/stub/stub-refinement.service.ts` (keeps initial order, test-only failure switch); T016 passes.
- [x] T018 [P] Write `app/session/session-reactions.service.spec.ts`: starts empty; `set(restaurants, reactions)` exposes both signals; `restaurant(id)` lookup.
- [x] T019 Implement `app/session/session-reactions.service.ts` (in-memory, root-provided, written only by `DeckStore`); T018 passes.
- [x] T020 Register providers in `app/app.config.ts`: `{ provide: DeckSource, useClass: StubDeckSource }`, `{ provide: RefinementService, useClass: StubRefinementService }`, with a comment that feature 002 replaces them.

**Checkpoint**: contracts, stubs and session service exist and are tested.

---

## Phase 3: User Story 1 — Browse the proposed restaurants one card at a time (P1) 🎯 MVP

**Goal**: `/deck` shows the first card, the adaptive title and the progress.

**Independent Test**: with the stub, open `/deck`: title "Ça vous dit, ce soir ?", "1 sur 15", full card content and fallbacks (quickstart scenarios 1–2).

### Tests for User Story 1 (write first, see them fail)

- [x] T021 [P] [US1] Write `app/deck/format/distance.spec.ts` (D15): 349 → "350 m", 600 → "600 m", 1000 → "1,0 km", 1149 → "1,1 km", 1560 → "1,6 km". Boundary 995–999 m (rounds to 1000 m) → "1,0 km" (product owner, 2026-11-24).
- [x] T022 [P] [US1] Write `app/deck/format/opening.spec.ts` (D8): given `Restaurant.weeklyHours` and a `targetDateTime`, returns the closing time of the range containing it (two ranges a day, range ending after midnight, target on another weekday than today).
- [x] T023 [P] [US1] Write `app/deck/deck-state.spec.ts` — creation: `DeckState.size` = min(DECK_SIZE, restaurants) (FR-002, US5 AS2), first card current, `DeckState.planned` holds the rest (rule R2), position 1, `canRewind` false.
- [x] T024 [P] [US1] Write `app/deck/swipe-card/swipe-card.component.spec.ts` — content: photo when `Restaurant.photoUrl`, gingham tablecloth otherwise (US1 AS3, D16); distance formatted; name; cuisine chip then keywords; price chip hidden when no `Restaurant.priceLevel`; "Ouvert jusqu'à HH:MM"; "À goûter" + dish name, or default dish visual and no name when no `Restaurant.dish` (US1 AS4).
- [x] T025 [P] [US1] Write `app/deck/deck.component.spec.ts` — title from `DeckInput.momentLabel` ("Ça vous dit, ce soir ?", "Ça vous dit, samedi midi ?", "Ça vous dit, maintenant ?" when null); progress text "1 sur 15" and progress bar (D11); no "Vos coups de cœur" panel (FR-012); no card back or "Voir la fiche".

### Implementation for User Story 1

- [x] T026 [P] [US1] Implement `app/deck/format/distance.ts`; T021 passes.
- [x] T027 [P] [US1] Implement `app/deck/format/opening.ts`; T022 passes.
- [x] T028 [US1] Implement `app/deck/deck-state.ts` creation function and derived `position`, progress, `canRewind` per data-model.md `DeckState`; T023 passes.
- [x] T029 [US1] Implement `DeckStore.start()` and `state` signal in `app/deck/deck.store.ts` (loads `DeckSource`, builds `DeckState`, writes `SessionReactionsService`), with `app/deck/deck.store.spec.ts` written first using a fake `DeckSource`.
- [x] T030 [US1] Implement `app/deck/swipe-card/swipe-card.component.ts/.html/.css` (card front, design mapping in plan.md, next card peeking behind); T024 passes.
- [x] T031 [US1] Implement `app/deck/deck.component.ts/.html/.css` (header with Nappe logo and "Voir la liste" on desktop, title, progress row with "<" placeholder slot at left and mobile "Voir la liste" pill at right (D3, D10), card area, button row; mobile layout below 768 px (D5)); T025 passes.
- [x] T032 [US1] Add route `deck` → `DeckComponent` in `app/app.routes.ts` (D14).

**Checkpoint**: US1 works alone at `/deck`.

---

## Phase 4: User Story 2 — React to each card (P1)

**Goal**: dislike, like, pick by gesture or button; "Voir la liste"; placeholders for 005/006.

**Independent Test**: quickstart scenarios 3, 4, 5, 10.

### Tests for User Story 2 (write first, see them fail)

- [x] T033 [P] [US2] Write `app/deck/swipe-card/swipe-gesture.spec.ts` (D5): under 6 px → no drag; dx ≥ 110 → right; dx ≤ −110 → left; dy ≤ −110 with |dy| > |dx| → up; under thresholds → none; diagonal cases.
- [x] T034 [P] [US2] Extend `app/deck/deck-state.spec.ts` — `react(liked|disliked)` records `Reaction.kind` and moves to the next card; `react(picked)` sets status `picked`; `returnFromPick` turns `picked` into `liked` and moves to the next card (FR-014).
- [x] T035 [P] [US2] Extend `app/deck/deck.store.spec.ts` — `react` updates `SessionReactionsService` (FR-011); pick navigates to `/route/:id` (FR-008) (Router spy).
- [x] T036 [P] [US2] Extend `app/deck/deck.component.spec.ts` — buttons "✕"/"♥"/"→" with accessible names "Pas envie"/"Miam"/"On y va" call the right reaction (D4); "Voir la liste" navigates to `/results` (FR-009).
- [x] T037 [P] [US2] Extend `app/deck/swipe-card/swipe-card.component.spec.ts` — pointer drag beyond threshold emits the reaction; release under threshold returns the card; "Miam"/"Pas envie" stamps follow the drag (mockup). If jsdom lacks `PointerEvent`, use the research.md fallback.
- [x] T038 [P] [US2] Write `app/placeholders/route-placeholder.component.spec.ts` — shows the restaurant name from `SessionReactionsService`; "Retour aux propositions" calls `DeckStore.returnFromPick()` and navigates to `/deck`; "Voir la liste" navigates to `/results` (D13).
- [x] T039 [P] [US2] Write `app/placeholders/results-placeholder.component.spec.ts` — shows "Résultats — bientôt disponible" and the recorded reactions (D13).

### Implementation for User Story 2

- [x] T040 [P] [US2] Implement `app/deck/swipe-card/swipe-gesture.ts` (`classifySwipe`); T033 passes.
- [x] T041 [US2] Implement `react` and `returnFromPick` in `app/deck/deck-state.ts`; T034 passes.
- [x] T042 [US2] Implement `react` and `returnFromPick` in `app/deck/deck.store.ts` with navigation; T035 passes.
- [x] T043 [US2] Add pointer drag (Pointer Events, `setPointerCapture`, `touch-action: none` on the card) and stamps to `app/deck/swipe-card/`; T037 passes.
- [x] T044 [US2] Wire buttons and "Voir la liste" in `app/deck/deck.component.*`; T036 passes.
- [x] T045 [P] [US2] Implement `app/placeholders/route-placeholder.component.ts`; T038 passes.
- [x] T046 [P] [US2] Implement `app/placeholders/results-placeholder.component.ts`; T039 passes.
- [x] T047 [US2] Add routes `results` and `route/:id` in `app/app.routes.ts`.

**Checkpoint**: US1 + US2 work; reactions reach the placeholders.

---

## Phase 5: User Story 3 — Get better proposals as I react (P2)

**Goal**: remaining cards recomputed after each reaction through `RefinementService`, same size, no repeat, failure tolerated.

**Independent Test**: quickstart scenarios 6, 7. SC-004 not testable with the stub (decided).

### Tests for User Story 3 (write first, see them fail)

- [x] T048 [P] [US3] Extend `app/deck/deck-state.spec.ts` — `applyRefinement` per data-model.md rules R1–R3: reacted restaurants removed (R1, SC-003), truncated to slots left, completed with previous planned cards when short (R2 kept, US3 AS2); `DeckState.refinementDegraded` true on error, false on next success.
- [x] T049 [P] [US3] Extend `app/deck/deck.store.spec.ts` with fake `RefinementService` — after a reaction, `refine` called with active reactions, `excludedIds`, `slots`; next card shown only after the result (D2); newer call cancels older; on error the next planned card is shown and degraded is set (FR-013); next success clears it.
- [x] T050 [P] [US3] Extend `app/deck/deck.component.spec.ts` — degraded message "Suggestions non personnalisées pour le moment" shown non-blocking when degraded, hidden otherwise.

### Implementation for User Story 3

- [x] T051 [US3] Implement `applyRefinement` in `app/deck/deck-state.ts`; T048 passes.
- [x] T052 [US3] Call `RefinementService` from `app/deck/deck.store.ts` (wait for result, `switchMap`, error path); T049 passes.
- [x] T053 [US3] Show the degraded message in `app/deck/deck.component.*`; T050 passes.

**Checkpoint**: refinement loop works with the stub, including failure.

---

## Phase 6: User Story 4 — Undo a mistake (P2)

**Goal**: "<" rewinds card by card to the first card.

**Independent Test**: quickstart scenarios 8, 12.

### Tests for User Story 4 (write first, see them fail)

- [x] T054 [P] [US4] Extend `app/deck/deck-state.spec.ts` — `rewind` makes the previous card current and cancels its reaction (US4 AS1); repeated to the first card (AS2); no-op on the first card (AS3); rewound-past restaurants eligible again (D7).
- [x] T055 [P] [US4] Extend `app/deck/deck.store.spec.ts` — rewind triggers a refinement without the cancelled reaction (AS4) and updates `SessionReactionsService`.
- [x] T056 [P] [US4] Extend `app/deck/deck.component.spec.ts` — "<" top-left with accessible name "Revenir à la carte précédente" (D3, D4), disabled on the first card.

### Implementation for User Story 4

- [x] T057 [US4] Implement `rewind` in `app/deck/deck-state.ts`; T054 passes.
- [x] T058 [US4] Implement `rewind` in `app/deck/deck.store.ts`; T055 passes.
- [x] T059 [US4] Add the "<" button in `app/deck/deck.component.*`; T056 passes.

**Checkpoint**: rewind works with refinement.

---

## Phase 7: User Story 5 — Reach the end of the deck (P3)

**Goal**: end of deck opens results; short decks.

**Independent Test**: quickstart scenarios 9, 11.

### Tests for User Story 5 (write first, see them fail)

- [x] T060 [P] [US5] Extend `app/deck/deck-state.spec.ts` — like/dislike on the last card sets status `finished` (US5 AS1); fewer restaurants than DECK_SIZE gives that size and progress total (AS2); `returnFromPick` on the last card sets `finished` (D12).
- [x] T061 [P] [US5] Extend `app/deck/deck.store.spec.ts` — `finished` navigates to `/results`, including after dislike of every card and after return from a pick on the last card.

### Implementation for User Story 5

- [x] T062 [US5] Implement end-of-deck transitions in `app/deck/deck-state.ts`; T060 passes.
- [x] T063 [US5] Navigate to `/results` on `finished` in `app/deck/deck.store.ts`; T061 passes.

**Checkpoint**: all stories work independently.

---

## Phase 7b: Swipe animation and haptic feedback (D18, D19 — added after the first manual test)

**Independent Test**: on a phone, each reaction makes the card fly off in its direction, the next card rises, and (Android) the phone vibrates.

- [x] T069 [P] [US2] Write `app/deck/haptics.spec.ts`: pattern 15 ms for liked and disliked, [15, 60, 15] for picked (D19); no error when `navigator.vibrate` is missing.
- [x] T070 [US2] Implement `app/deck/haptics.ts`; T069 passes.
- [x] T071 [P] [US2] Extend `app/deck/swipe-card/swipe-card.component.spec.ts`: a validated drag emits `swiped` at once, then `left` after the animation (`SWIPE_ANIMATION_MS`); the card gets the leaving transform of its direction; `leave(direction)` does the same for buttons; gestures are ignored while leaving.
- [x] T072 [US2] Implement the leave animation, `left` output, `leave()` method and `SWIPE_ANIMATION_MS` token (300 ms, 0 with reduced motion) in `app/deck/swipe-card/`; T071 passes.
- [x] T073 [P] [US2] Extend `app/deck/deck.component.spec.ts`: buttons and swipes vibrate with the D19 pattern and apply the reaction only after the animation; the next card is a new card instance (rise animation).
- [x] T074 [US2] Wire haptics and animation in `app/deck/deck.component.*` (buttons call the card's `leave`, reaction applied on `left`, card re-created per restaurant); T073 passes.
- [x] T075 [P] [US2] (superseded by T077) Extend `app/deck/haptics.spec.ts` (D19, iPhone): without `navigator.vibrate`, a hidden, aria-hidden label wrapping an `<input type="checkbox" switch>` is clicked once and removed from the page; with `navigator.vibrate`, no switch is used.
- [x] T076 [US2] (superseded by T078) Implement the iPhone switch workaround in `app/deck/haptics.ts`; T075 passes.
- [x] T077 [P] [US2] Rework `app/deck/haptics.spec.ts` and extend `app/deck/deck.component.spec.ts` (D19, iPhone): T075/T076's programmatic toggle is removed (no tick on iOS 18.7, checked on device); each reaction button is covered by a transparent `<input type="checkbox" switch>` (aria-hidden, tabindex -1) whose tap triggers the same reaction; it is disabled while a refinement is pending.
- [x] T078 [US2] Implement the switch overlay in `app/deck/deck.component.*` and remove the programmatic toggle from `app/deck/haptics.ts`; T077 passes.
- [x] T079 [P] [US2] (tried, then reverted: no tick on swipes on iOS 18.7, a tap on the card ticked without reaction) Extend `app/deck/swipe-card/swipe-card.component.spec.ts` (D19 experiment): a transparent `<input type="checkbox" switch>` (aria-hidden, tabindex -1) covers the card, disabled while disabled or leaving; a drag started on it still swipes; pointer capture is taken for a mouse but not for a finger (touch is captured by its target, so the switch keeps the finger).
- [x] T080 [US2] (reverted after the iPhone check; tests and overlay removed, touch pointer capture restored) Implement the card switch overlay in `app/deck/swipe-card/`; T079 passes. Check on iPhone; remove T079/T080 if swipes do not tick.

---

## Phase 8: Polish and cross-cutting

- [x] T064 Run `npx --prefix frontend prettier --write` on changed frontend files (`frontend/.prettierrc`).
- [x] T065 Run `npm --prefix frontend test -- --watch=false` (full suite) and `npm --prefix frontend run build` (bundle budgets 500 kB / 1 MB).
- [ ] T066 Manual check of quickstart.md scenarios 1–13 at 1280 px (mouse) and 375 px (touch emulation); report results.
- [x] T067 Independent review by the `relecteur` agent against spec.md (FR-001–FR-014, SC-002, SC-003), constitution and DRY; fix confirmed findings.
- [ ] T068 Prepare PR notes: decisions D1–D17, still-open items (SC-001, SC-004, `targetDateTime` with 002, reconciliation with 001's `SearchService`, identical shell files with 001), DRY report (reused: 001 shell; nothing else to reuse on `main`).

---

## Dependencies and execution order

- Phase 1 → Phase 2 → US1 → US2 → (US3, US4, US5 in any order; all edit `deck-state.ts` and `deck.store.ts`, so run them sequentially) → Polish.
- US2 needs US1's deck screen and state. US3–US5 need US2's `react`.
- Inside each story: tests before implementation; `deck-state` before `deck.store` before components.

## Parallel opportunities

- Phase 1: T002, T003 together.
- Phase 2: T006–T012, T014, T016, T018 together (different files).
- US1: T021–T025 together, then T026 and T027 together.
- US2: T033–T039 together; T045 and T046 together.
- US3, US4, US5: their test tasks are parallel within the story.

## Implementation strategy

1. MVP = Phases 1–3 (US1): the deck displays correctly at `/deck`.
2. Add US2 (reactions and navigation): the deck is usable end to end with the placeholders.
3. Add US3, US4, US5 in priority order.
4. Polish, review by `relecteur` (mode b), then the human commits and opens the PR.
