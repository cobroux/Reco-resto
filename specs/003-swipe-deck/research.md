# Research: Swipe Deck

Phase 0 of `/speckit-plan` for [spec.md](./spec.md). Each topic: decision, rationale, alternatives. Values not given by the spec were decided by the product owner on 2026-11-24 (plan, Decisions D1–D17).

## 1. Data source before feature 002 exists

- **Decision**: abstract classes `DeckSource` and `RefinementService` used as injection tokens, with stub implementations provided in `app.config.ts`. Feature 002 swaps the providers.
- **Rationale**: same pattern as 001 (`SearchService` + `StubSearchService`); the screen never knows which implementation runs. Product owner decision (frontend only, stubs).
- **Alternatives**: wait for 002 (blocks the team); call a mock HTTP backend (no backend exists, and 002 has not chosen its API).

## 2. Contract separate from 001

- **Decision**: 003 defines `DeckSource` itself; it does not import 001's `SearchService`.
- **Rationale**: product owner decision; 003 is built on `main`.
- **Alternatives**: rebase on `feat/home-search-screen` (rejected by the product owner). Reconciliation at merge is recorded in the plan's Complexity Tracking.

## 3. Gestures

- **Decision**: native Pointer Events (`pointerdown/move/up/cancel`, `setPointerCapture`) on the card, `touch-action: none` on the card only. Direction is computed by a pure function `classifySwipe(dx, dy)` returning `left | right | up | none`.
- **Rationale**: one code path for mouse and touch (FR-005); no dependency; the mockup prototype already works this way (threshold 110 px, drag starts after 6 px). A pure classifier is unit-testable without DOM events.
- **Alternatives**: HammerJS (unmaintained, extra dependency); `@angular/cdk/drag-drop` (list-oriented, extra dependency, no swipe semantics).
- **Thresholds** (D5): horizontal 110 px and drag start 6 px from the mockup; up-swipe 110 px with vertical movement dominant.
- **Unverified**: jsdom support for `PointerEvent` in component tests (fallback: dispatch `MouseEvent`-based synthetic events or test through the classifier and button handlers).

## 4. Deck state

- **Decision**: a pure `DeckState` (immutable value + functions `react`, `rewind`, `returnFromPick`, `applyRefinement`) wrapped by a root `DeckStore` holding Angular signals.
- **Rationale**: FR-006, FR-007, FR-014 are list rules; testing them as pure functions is fast and complete. Signals fit Angular 22 and 001's style.
- **Alternatives**: NgRx (too heavy for one screen); state inside the component (hard to test, not shareable with 005/006).

## 5. Refinement call

- **Decision**: after each reaction or rewind, `DeckStore` calls `RefinementService.refine(request)` with all active reactions, the ids already reacted, and the number of slots left. The result replaces the planned cards after the current position, after filtering out reacted ids and truncating to the slots left. A newer call cancels an older pending one (`switchMap`).
- **Rationale**: keeps the deck size fixed (FR-006, US3 AS2) and guarantees no repeat (SC-003) even if the service misbehaves.
- **Timing** (D2): the next card is shown once the refinement result arrives (US3 AS1); no timeout until SC-001 is set.
- **Failure**: on error the planned cards stay, `refinementDegraded` becomes true and the message "Suggestions non personnalisées pour le moment" is shown; the next successful call clears it (FR-013).
- **Stub**: returns the planned cards in their initial order (product owner decision); a test-only switch makes it fail.

## 6. Session reactions

- **Decision**: root service `SessionReactionsService` holding, in memory, the deck restaurants and the active reaction of each (`liked | disliked | picked`), read by 005 and 006.
- **Rationale**: FR-011 (session only, available to 005). No storage: refresh loses everything, as stated in the edge cases.
- **Alternatives**: `sessionStorage` (would survive refresh, contradicts the spec); router state (lost on navigation back).

## 7. Navigation

- **Decision**: routes `/deck`, `/results`, `/route/:id`. The two last ones are placeholders replaced by 005 and 006.
- **Rationale**: product owner decision (placeholder routes). Routes and placeholder texts decided (D13, D14).

## 8. Card display rules

- **Opening status**: the deck input carries the targeted date-time (D8, to confirm in 002's plan); `opening.ts` returns the end of the opening range containing it ("Ouvert jusqu'à 23:00"). Restaurants closed at that moment never reach the deck (002 rule).
- **Distance**: `distance.ts` below 1 km: metres rounded to 10 m ("350 m"); from 1 km: one decimal with a French comma ("1,1 km") (D15).
- **Missing photo**: gingham pattern from the Nappe logo of the mockup (CSS gradients). **Missing dish**: neutral inline-SVG dish icon, no name (US1 AS4). Both are CSS placeholders until designed (D16).
- **Keywords**: cuisine chip first (coloured), then keywords, then price level, as in the mockup.

## 9. Styling and responsive layout

- **Decision**: plain CSS with the mockup values as CSS variables in `styles.css`; Outfit from Google Fonts. One media query for mobile.
- **Rationale**: same as 001, no dependency.
- **Breakpoint** (D5): mobile layout below 768 px.

## 10. Testing strategy

- Pure unit tests: `deck-state`, `swipe-gesture`, `distance`, `opening`, stubs.
- Store tests with fake `DeckSource` / `RefinementService` (success, failure, recovery).
- Component tests (TestBed, jsdom): texts, card content and fallbacks, buttons, progress, no side panel, navigation calls (Router spy).
- Manual check in the browser at 1280 px and 375 px (quickstart).
- SC-004 is not testable with the stub (product owner decision).
