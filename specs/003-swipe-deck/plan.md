# Implementation Plan: Swipe Deck

**Branch**: `feat/swipe-deck` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-swipe-deck/spec.md`

## Summary

Build the Nappe swipe deck in the Angular frontend: the search results are shown one card at a time under the title "Ça vous dit, <moment> ?" with a progress "X sur N". The user dislikes (swipe left / "✕"), likes (swipe right / "♥") or picks (swipe up / "→") the current card, rewinds with "<", and can open the results list at any time. After each reaction or rewind, the remaining cards are recomputed through a refinement service. Reactions are kept in memory for the session and exposed to features 005 and 006.

Decided with the product owner on 2026-11-24:

- **Frontend only, with stubs.** Feature 002 (language model, restaurant source, backend) is not designed yet. 003 defines its own injectable contract (`RefinementService`) and reads the search result of 001's `SearchService`, with stub implementations that 002 will replace by providing other classes, without touching the deck screen.
- **Contract reconciled with 001 (2026-11-25).** 003 was first built with its own `DeckSource`. After merging `main` (001), `DeckSource` was removed: 001's `SearchService` now returns the `DeckInput`, kept in `SearchResultStore` and read by the deck (spec FR-019–FR-021).
- **The next card waits for the refinement result** (US3 AS1); on failure the deck goes on with the planned cards (FR-013).
- **The refinement stub keeps the initial order.** The refinement call, its failure path (FR-013) and the no-repeat rule (FR-006) are implemented and tested; the "liked cuisine goes up" effect (US3 AS1 effect, SC-004) is verified when 002 provides the real refinement.
- **Placeholder routes** for 005 (`/results`) and 006 (`/route/:id`), replaced by those features.
- **SC-001 deferred to feature 002**, whose language model drives the latency.
- **Moment is a raw text from 002** ("ce soir", "samedi midi"); when absent, the title uses "maintenant".
- **Visual reference**: Claude Design canvas "Appli restaurants à proximité", page "App web v2", boards `V2WebSwipe` (1440 px) and `V2MobileSwipe` (390 px). Where mockup and spec differ, the spec wins (see Design Mapping).

## Technical Context

**Language/Version**: TypeScript 6.0, Angular 22.3 (standalone components, signals, new control flow)

**Primary Dependencies**: `@angular/core`, `@angular/router`, `rxjs` (already installed). Gestures with native Pointer Events. Font Outfit (Google Fonts), as in the mockup and in 001. No new npm dependency.

**Storage**: in-memory only (root-provided service). Lost on refresh or tab close, as the spec requires.

**Testing**: Vitest 5 with jsdom through `ng test` (`npm --prefix frontend test -- --watch=false`); tests next to the code as `*.spec.ts`. Deck logic is a pure, DOM-free state machine so most rules are unit-tested without gestures.

**Target Platform**: web browsers, desktop (mouse) and mobile (touch); test widths 1280 px and 375 px.

**Project Type**: web application, frontend part only.

**Performance Goals**: SC-001 deferred to feature 002 (D1). Not invented.

**Constraints**: French UI texts word for word from the spec; deck size configurable, 15 by default (FR-002); no "Vos coups de cœur" panel (FR-012); session-only reactions (FR-011).

**Scale/Scope**: one deck screen, two placeholder screens, three services, one stub data set.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | How the plan complies |
|-----------|--------|----------------------|
| I. Spec-Driven Development | Pass | Spec is Draft; spec and plan go to teammate review before `/speckit-tasks`. Nothing is implemented in this step. |
| II. Test-First | Pass | Tasks will put every `*.spec.ts` (deck state, gesture, services, component) before the code it covers. |
| III. Never Guess | Pass | Every value not given by the spec or the mockup was decided by the product owner on 2026-11-24 (Decisions D1–D17). Items owned by feature 002 are listed as still open. |
| IV. DRY | Pass with justified deviation | Repo searched (`main`): nothing to reuse (empty routes, placeholder shell). The temporary deck contract separate from 001's `SearchService` was removed at merge: one search contract, one stub. Inside 003: one texts file, one deck-size token, one distance formatter, one opening-hours helper. |
| V. English and Conventional Commits | Pass | Code and docs in English; only UI texts are French, as the spec requires. |

**Post-design re-check (after Phase 1)**: unchanged. The design adds no backend, no dependency and no duplicated rule beyond the justified deviation.

## Project Structure

### Documentation (this feature)

```text
specs/003-swipe-deck/
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── service-contracts.md   # DeckSource, RefinementService, SessionReactions, DECK_SIZE
│   └── ui-contract.md         # routes, screen content, controls, texts
└── tasks.md             # created later by /speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/
├── index.html                          # lang="fr", Outfit font link (shared with 001, see D9)
├── styles.css                          # design tokens as CSS variables (shared with 001, see D9)
└── app/
    ├── app.html / app.spec.ts          # placeholder replaced by <router-outlet /> (shared with 001)
    ├── app.routes.ts                   # '/deck', '/results', '/route/:id'
    ├── deck/
    │   ├── deck.texts.ts               # single source of every French text of the deck
    │   ├── deck-state.ts               # pure state machine: react, rewind, pick, return, refine
    │   ├── deck-state.spec.ts
    │   ├── deck.store.ts               # signals wrapper; calls DeckSource and RefinementService
    │   ├── deck.store.spec.ts
    │   ├── deck.component.ts / .html / .css / .spec.ts   # screen: header, title, progress, card, buttons
    │   ├── swipe-card/
    │   │   ├── swipe-card.component.ts / .html / .css / .spec.ts   # card front + pointer drag
    │   │   └── swipe-gesture.ts / .spec.ts                         # pure: pointer deltas -> left/right/up/none
    │   ├── format/
    │   │   ├── distance.ts / .spec.ts          # "350 m", "1,1 km"
    │   │   └── opening.ts / .spec.ts           # "Ouvert jusqu'à 23:00" for the targeted day/time
    │   ├── deck-source.ts                      # abstract contract + DeckInput type
    │   ├── refinement.service.ts               # abstract contract
    │   ├── deck-size.token.ts                  # DECK_SIZE injection token, default 15
    │   └── stub/
    │       ├── stub-restaurants.ts             # fixed data set (D6)
    │       ├── stub-deck-source.ts / .spec.ts
    │       └── stub-refinement.service.ts / .spec.ts   # keeps initial order; can be set to fail
    ├── session/
    │   ├── session-reactions.service.ts / .spec.ts     # reactions of the session, read by 005/006
    ├── restaurant/
    │   └── restaurant.ts                               # Restaurant view model (shape of 002 FR-012 fields used here)
    └── placeholders/
        ├── results-placeholder.component.ts / .spec.ts # replaced by feature 005
        └── route-placeholder.component.ts / .spec.ts   # replaced by feature 006; "back to deck" (FR-014)
```

**Structure Decision**: feature-oriented folders in `frontend/src/app/`, same convention as 001 (`home/`, `search/`). Contracts are separated from stubs so feature 002 replaces only providers. Deck rules live in a pure `deck-state.ts`, so FR-006/FR-007/FR-014 are tested without the DOM.

## Requirement Traceability

| Requirement | Where |
|-------------|-------|
| FR-001 title, progress | `deck.component`, `deck.texts.ts` |
| FR-002 deck size | `DECK_SIZE` token, `deck-state.ts` (N = min(size, restaurants)) |
| FR-003 card content, fallbacks | `swipe-card.component`, `format/*` |
| FR-004, FR-005 reactions, gestures, buttons | `swipe-gesture.ts`, `swipe-card.component`, `deck.component` |
| FR-006 recompute, same size, no repeat | `deck-state.ts`, `deck.store.ts`, `RefinementService` |
| FR-007 rewind | `deck-state.ts` |
| FR-008 pick → route | `deck.store.ts`, `/route/:id` placeholder |
| FR-009, FR-010 results | `/results` placeholder |
| FR-011 session reactions | `SessionReactionsService` |
| FR-012 no side panel | `deck.component` (not built; asserted in test) |
| FR-013 refinement failure | `deck.store.ts`, failing stub in tests |
| FR-014 return from route | `deck-state.ts` (picked → liked), route placeholder |
| FR-015 tablecloth under the stack, no card behind | `deck.component` (`.table`) |
| FR-017 "En cuisine…" crêpe toss | `cooking-scene.component`, `deck.texts.ts` |
| FR-018 one full toss before the next card | `COOKING_TOSS_MS` token, `deck.store.ts` |
| FR-016 no refinement in advance | `deck.store.ts` (unchanged, D2) |

## Design Mapping (mockup to implementation)

Values read from `V2WebSwipe.dc.html` and `V2MobileSwipe.dc.html`.

| Item | Desktop | Mobile |
|------|---------|--------|
| Page | background `#F5F3FC`, max width 1280 px, header with logo and "Voir la liste" pill (44 px, white) | 390 px, no header |
| Column | max width 420 px, centred | full width, padding 16/20 px |
| Title | 36 px bold, line-height 1.1 | 28 px |
| Progress | "X sur N" 15 px `#6A6578` + thin bar (`#5B4BC4` done, `#E4E0F2` to come; mockup dot colours) (D11) | same, plus "Voir la liste" pill at the right (D10) |
| Card | 560 px high, radius 32 px, white, shadow `0 24px 60px rgba(76,60,160,.16)`; no card peeking behind (D20); the next card rises from a smaller, rotated position (rotate 3°, scale .95) | fills the height left by the progress, title and buttons so they show without scrolling (min 340 px, then the page scrolls); the photo absorbs the difference (min 120 px) |
| Hero | 300 px, distance pill bottom-left | 384 px |
| Body | name 26 px bold; chips 28 px (cuisine coloured, keywords `#E3F5E1`/`#2F7A3A`, price `#F5F3FC`); two tiles "Ouvert" (`#E3F5E1`) and "À goûter" (`#FDEBDD`/`#A24E12`) | name 22 px |
| Buttons | 64 px white round, centre 84 px `#5B4BC4` | same |
| Drag | card follows pointer, rotation dx/18°, stamps "Miam"/"Pas miam" fade in, threshold 110 px, drag starts after 6 px; the stamp differs from the mockup for visibility (2026-11-25): the photo is tinted (violet / raspberry) and a large tilted stamp "♥ MIAM" / "✕ PAS MIAM" sits in its centre | same, stamp text 32 px |

Spec overrides applied on top of the mockup:

1. "Nappe" instead of "Tablée".
2. No "Vos coups de cœur" side panel (FR-012).
3. Buttons are "✕" (dislike), "♥" (like), "→" (pick). The mockup's third button "Passer" (skip) does not exist in the spec.
4. "<" rewinds (FR-007) and takes the place of the mockup's top-left "Retour" arrow; no home link on the deck (D3).
10. Progress shows "X sur N" and a thin bar instead of dots (D11); on mobile, "Voir la liste" is a pill at the right of the progress row (D10); mobile layout below 768 px (D5).
5. Swipe up = pick; not in the mockup.
6. No card flip, no "Voir la fiche", no card back: feature 004.
7. No end-of-deck card ("coups de cœur", "Recommencer"): the results screen opens (FR-010).
8. Title built from the moment instead of the fixed "ce soir".
9. Photo in the hero when available; otherwise a tablecloth pattern in the style of the Nappe logo (the mockup logo is a violet gingham square: `rgba(91,75,196,.55)` crossed gradients, 14 px).

## Decisions on open points (product owner, 2026-11-24)

| # | Topic | Decision |
|---|-------|----------|
| D1 | SC-001 threshold | Deferred to feature 002; not testable until then |
| D2 | Refinement timing | The next card is shown once the refinement result arrives (US3 AS1); no timeout until SC-001 is set; on error the next planned card is shown with the FR-013 message |
| D3 | "<" placement | Top-left, where the mockup's arrow is; no home link on the deck |
| D4 | Button labels (visible and accessible) | "✕" = "Pas miam", "♥" = "Miam", "→" = "On y va", "<" = "Revenir à la carte précédente" |
| D5 | Gesture thresholds and breakpoint | Horizontal 110 px and drag start 6 px (mockup); up-swipe 110 px with vertical movement dominant; mobile layout below 768 px |
| D6 | Stub data set | 16 fictional Paris restaurants (the 15 cap is visible), mixed cuisines, at least one without photo and one without dish |
| D7 | Rewound-past cards | Eligible again: only restaurants with an active reaction are excluded from the following cards |
| D8 | Targeted day and time | `DeckSource` provides `targetDateTime` (ISO, computed by 002); the card shows the end of the opening range containing it |
| D9 | Shell files shared with 001 | 003 writes the same tokens, font link and shell as 001; whoever merges second resolves the identical conflict |
| D10 | "Voir la liste" on mobile | Pill at the right of the progress row, mobile only |
| D11 | Progress display | "X sur N" plus a thin progress bar; no dots |
| D12 | Pick on the last card, then return | Opens the results screen (FR-010); the picked restaurant counts as liked |
| D13 | Placeholder texts | `/results`: "Résultats — bientôt disponible" and the list of recorded reactions; `/route/:id`: restaurant name, "Retour aux propositions", "Voir la liste" |
| D14 | Deck route | `/deck` |
| D15 | Distance format | Below 1 km: metres rounded to 10 m ("350 m"); from 1 km: kilometres with one decimal and a French comma ("1,1 km") |
| D16 | Default visuals | CSS placeholders until designed: gingham pattern from the Nappe logo for a missing photo; neutral inline-SVG dish icon for a missing dish |
| D17 | Deck size | Frontend `DECK_SIZE` token (15) until 002 owns the value; then the token is removed |
| D18 | Swipe animation (added after the first manual test) | The released card flies off in the swipe direction (left, right, up) with rotation and fade; the next card rises from the peek position. Same animation for the "✕", "♥", "→" buttons. Duration 300 ms; none when the user prefers reduced motion. The reaction is applied once the animation ends |
| D19 | Haptic feedback (added after the first manual test) | On each validated reaction (swipe or button): 15 ms vibration for "Pas miam" and "Miam", two 15 ms pulses 60 ms apart for "On y va". Web Vibration API on Android. iPhone (no Vibration API): unofficial workaround chosen by the product owner on 2026-11-24 — a transparent native switch (`<input type="checkbox" switch>`, hidden from assistive technologies) covers each "✕", "♥", "→" button; the finger's tap on it plays one system tick and triggers the reaction. Verified on iOS 18.7: a real tap ticks, a programmatic toggle does not. Consequences: one tick for every reaction, may stop working with an iOS update. On iPhone the tick is on the buttons only: a switch over the card was tried on 2026-11-24 and removed (no tick on swipes on iOS 18.7, and a plain tap on the card ticked without any reaction). Known limitation, confirmed by research on 2026-11-24: no documented way to get a tick during or after a swipe in iPhone Safari; WebKit does not implement the Vibration API ([W3C report](https://w3c.github.io/vibration/reports/implementation.html)), the switch trick needs a real tap ([Flarum #4694](https://github.com/flarum/framework/issues/4694), [card-credits #4](https://github.com/Jon1095/card-credits/pull/4)); the proposed [Web Haptics API](https://github.com/WICG/web-haptics) ships in no stable browser yet. Revisit if Safari ships it |
| D20 | Tablecloth under the stack (amendment of 2026-11-25) | The white card peeking behind the current card is removed (a card back "En cuisine…" was tried, then dropped by the product owner: the tablecloth alone fills the gap). The stack area shows the lavender gingham of the Nappe logo at low opacity (0.10, to validate on device), with rounded corners, starting below the title. No mask-image (it made the drag stutter on phone); the table is kept on its own compositing layer. The gingham is defined once as the `--nappe-gingham` custom property on the deck host and reused by the logo, the missing-photo visual (D16) and the table. No refinement in advance: D2 is kept (product owner, 2026-11-25) |
| D21 | "En cuisine…" crêpe toss (amendment of 2026-11-25) | Inline SVG pan (dark body, wooden handle) and SVG crêpe (golden, browned spots, resting inside the pan, slight squash on landing), with three wavy lavender steam wisps and a static shadow, centred on the tablecloth, under the current card (hidden by it at rest, revealed as soon as it moves). Loop of 1 s: the pan flicks, the crêpe rises about 90 px, flips (rotateX 180°) and lands. Only `transform` is animated (compositor only, no repaint of the tablecloth). After a reaction, the store waits for both the refinement and one toss (`COOKING_TOSS_MS`, 1000 ms, 0 with reduced motion) before showing the next card; rewind is not delayed. Reduced motion: pan and crêpe hidden, text only. Duration chosen by Claude, validated by the product owner on 2026-11-25 |
| D22 | Browser icon (2026-11-25) | The Angular default `favicon.ico` is replaced by the Nappe logo: `public/favicon.svg` (lavender gingham, same pattern as the header logo) and a 180 px square `public/apple-touch-icon.png` for iOS. Product owner chose to do it in this branch: `index.html` now differs from 001's (exception to D9); whoever merges second keeps these two icon links |

### Still open (outside 003's scope)

1. **SC-001** threshold: set in feature 002's plan.
2. **SC-004** controlled test set and success threshold: with feature 002's real refinement.
3. **`targetDateTime`** in `DeckInput`: to be confirmed in feature 002's plan.
4. ~~**Contract reconciliation** with 001's `SearchService` at merge~~: done on 2026-11-25 (amendment (b)).
5. **Constitution ratification date** is still `TODO` (not part of this feature).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| ~~Deck contract (`DeckSource`) separate from 001's `SearchService` (DRY)~~ resolved 2026-11-25: `DeckSource` removed | Product owner decision: 003 starts from `main` while 001 is not merged | Rebasing on `feat/home-search-screen` couples the two branches before review. Reconciliation task at merge: `SearchService.search()` must produce the `DeckInput` of `DeckSource`, or one of the two is removed. |
