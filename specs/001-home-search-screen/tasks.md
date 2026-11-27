# Tasks: Home Search Screen

**Input**: Design documents from `/specs/001-home-search-screen/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/ui-and-service-contract.md](./contracts/ui-and-service-contract.md), [quickstart.md](./quickstart.md)

**Tests**: Required (constitution principle II, Test-First). Every test task is written first and MUST fail before the code it covers exists.

**Organization**: Tasks are grouped by user story. Frontend only; all paths are under `frontend/src/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 or US2 (see spec.md)

## Decisions validated by the product owner (from plan.md, Open points)

- No maximum sentence length for now.
- Stub delay: 500 ms, temporary and unverified, to be replaced by feature 002.
- When the search ends: the in-progress state stops, the field keeps its text.
- Emptiness is tested on the trimmed value; the original untrimmed text is submitted.
- Mobile layout below 768 px.
- Renaming the branch to `feat/home-search-screen` is left to the humans.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: page-level configuration shared by both stories

- [X] T001 Set `lang="fr"`, title "Nappe" and the Google Fonts link for Outfit (weights 400 to 700) in `frontend/src/index.html`
- [X] T002 [P] Define the mockup palette as CSS variables (`#F5F3FC` page, `#E9E5FD` panel, `#5B4BC4` primary, `#23202E` text, `#4E4960` muted, `#6A6578` placeholder) and a base reset using Outfit in `frontend/src/styles.css`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: contract, texts, routing and shell that both stories depend on

**⚠️ CRITICAL**: no user story work can begin until this phase is complete

- [X] T003 [P] Write the failing test for `StubSearchService` in `frontend/src/app/search/stub-search.service.spec.ts`: `search(sentence)` emits once and completes after 500 ms (use fake timers), and does not alter the sentence
- [X] T004 [P] Write the failing shell test in `frontend/src/app/app.spec.ts`: replace the "Hello, frontend" assertion with: the app creates, renders a `router-outlet`, and shows no placeholder content
- [X] T005 Create the abstract `SearchService` with `search(sentence: string): Observable<void>` in `frontend/src/app/search/search.service.ts` (contract section 2 of `contracts/ui-and-service-contract.md`)
- [X] T006 Implement `StubSearchService` in `frontend/src/app/search/stub-search.service.ts` (500 ms delay, marked with a comment as a temporary stand-in for feature 002) and provide it as `SearchService` at root; T003 must pass
- [X] T007 [P] Create `frontend/src/app/home/home.texts.ts` as the single source of every French text: logo "Nappe", badge, title, subtitle, placeholder, button label, empty-search message, in-progress label, and the four tiles (label, background color, icon color). Texts exactly as in spec FR-001, FR-004, FR-007; in-progress label wording is not specified, use a short French label and flag it for validation in the PR
- [X] T008 Remove the Angular placeholder from `frontend/src/app/app.html` (keep only `<router-outlet />`), empty `frontend/src/app/app.css`, and drop the unused `title` signal in `frontend/src/app/app.ts`; T004 must pass
- [X] T009 Register the route `''` for `HomeComponent` in `frontend/src/app/app.routes.ts` (lazy or direct import; `HomeComponent` is created in T012)

**Checkpoint**: contract, stub, texts and routing exist; user stories can start

---

## Phase 3: User Story 1 - Describe what I want to eat and start the search (Priority: P1) 🎯 MVP

**Goal**: the user types a sentence and starts the search; validation, in-progress state and no double submission.

**Independent Test**: open the app, type a sentence, press "Commencer" or Enter, and check that the exact sentence reaches `SearchService.search` once; empty input shows the message and calls nothing.

### Tests for User Story 1 ⚠️ (write first, they MUST fail)

> Create `frontend/src/app/home/home.component.spec.ts` with a fake `SearchService` (spy) injected in place of the stub.

- [X] T010 [US1] In `frontend/src/app/home/home.component.spec.ts`: test contract items U4 (click on "Commencer" and Enter in the field each call `search` once with the unchanged sentence, including leading/trailing spaces) and U7 (field empty on opening; also empty again when the component is recreated)
- [X] T011 [US1] In the same spec: test U5 for `data-model.md` `Search sentence.sentence` and `emptyError` (empty string, spaces only: no call, message from `home.texts.ts` displayed; message disappears when the user types)
- [X] T012 [US1] In the same spec: test U6 for `data-model.md` `Screen state.searching` (while the fake search has not completed: in-progress label shown, button disabled, a second submission does not call `search`; after completion: state ends, button enabled, field text kept)

### Implementation for User Story 1

- [X] T013 [US1] Create `HomeComponent` in `frontend/src/app/home/home.component.ts` and `home.component.html`: standalone, a native `<form>` with one text `<input>` (placeholder and `aria-label` from `home.texts.ts`) and a `<button type="submit">` labelled "Commencer"; signals `sentence`, `searching`, `emptyError` as defined in `data-model.md` (`Screen state`); submit handler: ignore when `searching`, set `emptyError` when `Search sentence.sentence` is empty after trim, otherwise call `SearchService.search` with the original untrimmed value, set `searching`, and clear it on completion; no maximum length. T010 to T012 must pass
- [X] T014 [US1] Display the empty-search message and the in-progress state in `frontend/src/app/home/home.component.html` (message with `role="alert"`, button `disabled` and in-progress label while `searching`) and add minimal functional styles in `frontend/src/app/home/home.component.css`

**Checkpoint**: User Story 1 works and is testable independently (unstyled-to-mockup is acceptable here)

---

## Phase 4: User Story 2 - Understand what Nappe does at first glance (Priority: P2)

**Goal**: the screen shows the logo, badge, title, subtitle and four decorative tiles, faithful to the mockup, on desktop and mobile.

**Independent Test**: display the screen at 1280 px and 375 px and compare with the mockup boards `V2WebAccueil` and `V2MobileAccueil`, following `quickstart.md`.

### Tests for User Story 2 ⚠️ (write first, they MUST fail)

- [X] T015 [US2] In `frontend/src/app/home/home.component.spec.ts`: test U1 (logo, badge, title, subtitle, placeholder, button label are exactly the strings of `home.texts.ts`, and those strings equal the spec FR-001 text; the title is the only `h1`)
- [X] T016 [US2] In the same spec: test U2 and U3 (four tiles with the exact labels, clicking each tile triggers no call and no state change, tiles are not focusable or buttons, no element containing "Se connecter")

### Implementation for User Story 2

- [X] T017 [US2] Complete `frontend/src/app/home/home.component.html`: header with logo mark and "Nappe", badge with pin icon, `h1` title (two lines on desktop), subtitle, and the four non-interactive tiles rendered with `@for` from `home.texts.ts` (decorative icons `aria-hidden`, icon paths from `V2WebAccueil.dc.html`). No "Se connecter". T015 and T016 must pass
- [X] T018 [US2] Style the desktop layout in `frontend/src/app/home/home.component.css` per the Design Mapping table of `plan.md`: 1280 px max width, hero panel radius 40 px and padding 64 px, two columns, pill-shaped search form with violet button, 2x2 tile grid with the second column offset 32 px, title 60 px
- [X] T019 [US2] Add the mobile layout under 768 px in `frontend/src/app/home/home.component.css` per the Design Mapping table: stacked layout, panel radius 32 px, title 40 px, tiles smaller, field and button 56 px high, no horizontal scroll at 375 px
- [ ] T020 [US2] Manually check SC-002 at 1280 px and 375 px (quickstart manual checks 1 and 2) and note the result in the PR (jsdom cannot verify layout)

**Checkpoint**: both stories work independently; the screen matches the mockup

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T021 Run `npm --prefix frontend test -- --watch=false` and `npm --prefix frontend run build`; both must pass (constitution quality gate)
- [ ] T022 Run all `quickstart.md` manual checks (checks 3 to 5 for behavior) and record the outcome in the PR description
- [ ] T023 [P] Report in the PR: reused/replaced code (Angular placeholder in `app.html` and `app.spec.ts` replaced; nothing else reusable existed), the unvalidated in-progress label wording (T007), the unverified 500 ms stub delay, the 768 px breakpoint, and the branch rename still to do

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none; T001 and T002 in parallel
- **Foundational (Phase 2)**: after Setup; blocks both stories. Order: T003/T004/T007 (parallel) → T005 → T006 → T008 → T009
- **US1 (Phase 3)**: after Phase 2. T010 to T012 (tests, failing) → T013 → T014
- **US2 (Phase 4)**: after Phase 2; T013 must exist because it builds on `HomeComponent`. T015/T016 → T017 → T018 → T019 → T020
- **Polish (Phase 5)**: after both stories

### User Story Dependencies

- **US1 (P1)**: independent after Foundational
- **US2 (P2)**: edits the same files as US1 (`home.component.html/.css/.spec.ts`), so run it after US1, not in parallel

### Parallel Opportunities

- T001 and T002
- T003, T004 and T007
- No parallelism inside the stories: they share `home.component.*` files

### Parallel Example: Foundational start

```text
Task: "Write failing test for StubSearchService in frontend/src/app/search/stub-search.service.spec.ts"
Task: "Write failing shell test in frontend/src/app/app.spec.ts"
Task: "Create frontend/src/app/home/home.texts.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 then Phase 2
2. Phase 3 (US1)
3. **Stop and validate**: type a sentence, check the submission, the empty message and the in-progress state
4. Then Phase 4 for the mockup-faithful presentation

### Notes

- Tests first: run them and see them fail before writing the code they cover
- Commit per task or logical group with Conventional Commits (`test:`, `feat:`, `chore:`); agents commit only when asked and never push
- The spec and plan must not be edited during implementation; deviations go in the PR
