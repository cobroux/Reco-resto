# Tasks: Restaurant Card Details

**Input**: Design documents from `/specs/004-restaurant-card-details/` ([spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/ui-contract.md](./contracts/ui-contract.md), [quickstart.md](./quickstart.md))

**Tests**: required (constitution II, Test-First). In each phase, every `*.spec.ts` task comes before the code it covers and MUST fail first.

**Format**: `- [ ] [ID] [P?] [Story] Description with file path`. `[P]` = parallelizable (different files, no dependency on an incomplete task). Paths are relative to `frontend/src/app/`.

**Open proposals**: texts and values marked D-04-x in [plan.md](./plan.md) are used as proposed; any that the product owner changes must be changed in `deck.texts.ts` / the stub only.

## Phase 1: Setup

- [x] T001 Run `npm --prefix frontend test -- --watch=false` on the branch and note the baseline (all green before any change).

## Phase 2: Foundational (blocks both stories)

- [x] T002 [P] Write failing specs in `search/deck-input.spec.ts`: `toDeckInput` maps `menu` and `phone` of the search `Restaurant` to the deck `Restaurant` (data-model.md `Restaurant.menu`, `MenuItem.name`, `MenuItem.description`, `MenuItem.price`); absent `menu` stays absent.
- [x] T003 Add `MenuItem` and `menu?` to the API `Restaurant` in `search/search-result.ts`; add `MenuItem`, `menu?` and `phone?` to the deck `Restaurant` in `restaurant/restaurant.ts`; map them in `toDeckRestaurant` in `search/deck-input.ts` (makes T002 pass).
- [x] T004 [P] Write failing specs in `deck/format/opening.spec.ts` for an exported `targetWeekday(targetDateTime)` (Sunday = 0, ISO read as written, offset ignored; invalid string returns null).
- [x] T005 Extract `targetWeekday` in `deck/format/opening.ts` and make `closingTimeAt` use it (single weekday rule; existing `closingTimeAt` specs stay green) (makes T004 pass).
- [x] T006 [P] Write failing specs in `deck/format/weekly-hours.spec.ts`: rows Monday first with French day names, ranges "12:00 – 14:30 · 19:00 – 22:30", "Fermé" for an empty day, `targeted` flag on `targetWeekday` (data-model.md `Weekly hours row`); returns an empty list when no day has a range.
- [x] T007 Implement `deck/format/weekly-hours.ts` (makes T006 pass).
- [x] T008 [P] Add the back texts to `deck/deck.texts.ts`: "Horaires", "Adresse", "Itinéraire", "Menu", "Fermé", flip label, full-route label, travel mode labels (proposals D-04-2, D-04-11, D-04-12).

**Checkpoint**: model, mapping, weekday helper, weekly-hours rows and texts ready.

## Phase 3: User Story 1 - Flip a card to learn more (P1) 🎯 MVP

**Goal**: flip the current card and read hours, address and menu.

**Independent test**: on a card with complete data, flip it and check every section; flip back; check missing sections are absent (quickstart scenarios 1-5).

- [x] T009 [P] [US1] Write failing specs in `deck/swipe-card/swipe-card.component.spec.ts`: "↻" button and tap flip the card both ways (FR-001); a drag above the 6 px threshold does not flip (D-04-4); the back shows name, cuisine, distance, price, keywords, "Horaires" with the targeted day highlighted (FR-004), "Adresse", "Menu" with dishes (FR-002); no "Menu" when `menu` is absent or empty and no "Horaires" when no hours (FR-003); swiping is disabled on the back (D-04-3); the card is back on the front when a new card appears.
- [x] T010 [US1] Add the flip state, the flip button, tap handling and the back markup (header, keywords, hours rows, address, menu with `@if` guards) to `deck/swipe-card/swipe-card.component.ts` and `.html`; reuse `formatDistance` and `weekly-hours.ts`; a dish without description or price renders without that part (D-04-7). Phone is not displayed (D-04-8).
- [x] T011 [US1] Style the back in `deck/swipe-card/swipe-card.component.css`: `rotateY` flip, scroll inside the card when content is taller (US1 AS4), desktop and mobile layouts with the 768 px breakpoint of 003 (FR-009), values from the `V2WebSwipe` / `V2MobileSwipe` boards.
- [x] T012 [P] [US1] Add to `deck/deck.component.spec.ts` a failing check that the reaction buttons stay enabled while the back is shown (FR-008) and that a reaction on the back applies to this restaurant; adjust `deck/deck.component.*` only if the check fails.
- [x] T013 [P] [US1] Update `deck/stub/stub-restaurants.ts` (and `stub-restaurants.spec.ts`) so at least one stub restaurant has a menu, one has no menu and one has no hours. (done without a "no hours" stub: the stub set must stay open at the targeted moment, see stub-restaurants.spec.ts; hours-less case covered in swipe-card spec)

**Checkpoint**: US1 works alone and is demonstrable.

## Phase 4: User Story 2 - Preview the route without leaving the deck (P2)

**Goal**: "Itinéraire" shows travel times inside the card, without counting as a reaction.

**Independent test**: press "Itinéraire", check the times, react to the card, check the deck goes on; open the full route and come back (quickstart scenarios 6-7).

- [x] T014 [P] [US2] Write failing specs in `deck/stub/stub-route-preview.service.spec.ts`: times derived from `distanceMeters` with the proposed speeds (D-04-6), rounded up to the minute; one stub case without public transport; `modes` lists only available modes (data-model.md `RoutePreview.modes`).
- [x] T015 [US2] Create `deck/route-preview.service.ts` (abstract `RoutePreviewService`, `RoutePreview` type, per contracts/ui-contract.md) and `deck/stub/stub-route-preview.service.ts` (makes T014 pass). Mark the speeds as unvalidated in a comment.
- [x] T016 [P] [US2] Write failing specs in `deck/swipe-card/route-preview/route-preview.component.spec.ts`: shows one line per available mode, hides a mode without route, shows an error message on failure (D-04-11 wording), the full-route button navigates to `/route/:id` of the restaurant (FR-007).
- [x] T017 [US2] Implement `deck/swipe-card/route-preview/route-preview.component.ts`, `.html`, `.css` (makes T016 pass); no call to `DeckStore` (FR-006).
- [x] T018 [P] [US2] Add to `deck/swipe-card/swipe-card.component.spec.ts` failing checks: "Itinéraire" opens the preview in the card; opening it emits no reaction (FR-006); flipping back closes it.
- [x] T019 [US2] Wire `RoutePreviewComponent` and the `previewOpen` state in `swipe-card.component.ts` / `.html` (makes T018 pass).
- [x] T020 [US2] Provide `RoutePreviewService` with `StubRoutePreviewService` in `app.config.ts`; update `app.config.spec.ts` accordingly.
- [x] T021 [P] [US2] Add to `deck/deck.component.spec.ts` (or `deck.store.spec.ts`) a check that, after opening the preview and then reacting, the deck goes on and reactions are unchanged (SC-002); verify return from `/route/:id` keeps the card (003 FR-014 already covers it; add a check only if not covered).

**Checkpoint**: US1 and US2 work independently.

## Phase 5: Extra request - Logo link to home (outside the spec, plan D-04-1)

**Goal**: the logo at the top-left of the deck goes to the home page.

**Independent test**: press the logo on `/deck`: the home page opens (quickstart scenario 8).

- [x] T022 [P] [EX1] Write a failing spec in `deck/deck.component.spec.ts`: the logo is a link to `/` (no confirmation).
- [x] T023 [EX1] Make the logo a `routerLink="/"` in `deck/deck.component.html` and import `RouterLink` in `deck/deck.component.ts`; keep the look in `deck.component.css` (reset link color and underline) (makes T022 pass).

## Phase 6: Polish and cross-cutting

- [x] T024 Run `npm --prefix frontend test -- --watch=false` and `npm --prefix frontend run build`; all must pass.
- [ ] T025 Walk through [quickstart.md](./quickstart.md) at 1280 px and 375 px.
- [ ] T026 Report in the PR description: what was reused, extended or replaced (plan.md "Reuse report"), the amendment to 003 D3, and the unvalidated values (stub speeds, labels).

## Dependencies and order

- Phase 1 → Phase 2 → Phase 3 and Phase 4. Phase 4 touches `swipe-card.component.*` after Phase 3 (T018-T019 after T010), so do Phase 3 first; Phase 5 is independent of both and can run in parallel with Phase 4.
- Inside a phase, a spec task precedes its implementation task.
- Parallel examples: T002, T004, T006, T008 together; T014 and T016 together; T022 with any Phase 4 task.

## Implementation strategy

MVP = Phases 1-3 (US1). Then Phase 4 (US2), then Phase 5. Commit only when asked; no push, no PR (agents never push).
