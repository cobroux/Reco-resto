# Implementation Plan: Restaurant Card Details

**Branch**: `feat/restaurant-card-details` | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-restaurant-card-details/spec.md`

## Summary

Add the back of the swipe card (feature 003): the user flips the current card with a "↻" button or by tapping it, and sees the restaurant header (name, cuisine, distance, price), the keywords, the opening hours of the week with the targeted day highlighted, the address with an "Itinéraire" button, and the menu. Sections with no data are not displayed. "Itinéraire" opens a route preview inside the card (travel time on foot, by bike, by car, by public transport) that never counts as a reaction and offers a button to the full route screen (feature 006, `/route/:id`).

Decided with the product owner on 2026-11-25 (before this plan was written):

- **Menu and phone come from the search result.** The backend already returns `menu` (`MenuItem`: `name`, `description`, `price`, all strings) and `phone`; the frontend ignores them today. The frontend `Restaurant` view model and `toDeckInput` are extended in place.
- **Route preview behind an abstract service with a stub.** `RoutePreviewService` (contract) + `StubRoutePreviewService`, same pattern as `RefinementService` / `StubRefinementService`. Feature 006 provides the real implementation without touching the card.
- **Logo link (extra request, outside the spec).** The logo (tablecloth mark + "Nappe") at the top-left of the deck screen becomes a link to the home page `/`. No confirmation; the deck state is not kept.
- **Visual reference**: Claude Design canvas "Appli restaurants à proximité", page "App web v2", boards `V2WebSwipe` (1440 px) and `V2MobileSwipe` (390 px), card back. Where mockup and spec differ, the spec wins.

Everything not decided above is listed in "Decisions to validate" below, as a proposal to be confirmed in review. Nothing is implemented in this step.

## Technical Context

**Language/Version**: TypeScript 6.0, Angular 22.3 (standalone components, signals, new control flow), as in 003.

**Primary Dependencies**: `@angular/core`, `@angular/router`, `rxjs` (already installed). No new npm dependency. Flip animation in CSS (`transform: rotateY`), no library.

**Storage**: none. The flipped state and the preview state live in the card component (signals); they are lost when the card leaves.

**Testing**: Vitest 5 with jsdom through `ng test`; tests next to the code as `*.spec.ts`, written before the code (constitution II). Pure helpers (weekly hours rows, targeted weekday) are unit-tested without the DOM.

**Target Platform**: web browsers, desktop (mouse) and mobile (touch); test widths 1280 px and 375 px.

**Project Type**: web application, frontend only. No backend change: `menu` and `phone` already exist in `Restaurant.java`.

**Performance Goals**: none stated by the spec. Not invented.

**Constraints**: French UI texts ("Horaires", "Adresse", "Itinéraire", "Menu") word for word from the spec; a section without data is not rendered (FR-003); reaction buttons stay available on the back (FR-008); responsive (FR-009).

**Scale/Scope**: one card back, one route preview, one service contract + stub, one logo link.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | How the plan complies |
|-----------|--------|----------------------|
| I. Spec-Driven Development | Pass, pending review | Spec and this plan go to a teammate for approval before `/speckit-tasks`. The logo link is outside spec 004 and contradicts decision D3 of 003 ("no home link on the deck"): the reviewer must accept it explicitly (see D-04-1). |
| II. Test-First | Pass | Tasks will put each `*.spec.ts` before the code it covers. |
| III. Never Guess | Pass with flagged items | Decisions from the product owner are listed above; every other value is in "Decisions to validate" and marked as unverified (notably the stub travel times). |
| IV. DRY | Pass | See "Reuse report". |
| V. English and Conventional Commits | Pass | Code and docs in English; only UI texts are French. |

**Post-design re-check (after Phase 1)**: unchanged. No backend, no dependency, no duplicated rule.

## Reuse report (DRY)

Searched the code base (`frontend/src/app`) for opening hours, distance, price, menu, address, routing and flip logic.

| Need | Found | Action |
|------|-------|--------|
| Distance text | `deck/format/distance.ts` | Reused on the back header |
| Weekday of the targeted moment | read inside `deck/format/opening.ts` (`closingTimeAt`) | **Extracted** into one exported helper in the same file, reused by `closingTimeAt` and by the day highlight (single rule) |
| Texts | `deck/deck.texts.ts` | **Extended** with the back texts |
| `Restaurant` view model | `restaurant/restaurant.ts` | **Extended** (`menu`, `phone`) |
| Search `Restaurant` type, mapping | `search/search-result.ts`, `search/deck-input.ts` | **Extended** (`menu`; `toDeckRestaurant` maps `menu`, `phone`) |
| Service contract + stub pattern | `deck/refinement.service.ts`, `deck/stub/` | Pattern **followed** for `RoutePreviewService` |
| Route screen | `placeholders/route-placeholder.component.ts` (`/route/:id`) | Reused as the target of the full-route button |
| Card front, keywords, price chips | `swipe-card.component.html` | Chips markup **moved** to a shared piece only if the back repeats it (decided in tasks; no copy) |
| Flip, menu, hours rows, preview | nothing relevant found | New |

## Project Structure

### Documentation (this feature)

```text
specs/004-restaurant-card-details/
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── ui-contract.md   # card back, route preview, logo link, services
└── tasks.md             # created later by /speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/app/
├── restaurant/restaurant.ts                 # + MenuItem, menu?, phone?
├── search/
│   ├── search-result.ts                     # + MenuItem, menu? on Restaurant
│   ├── deck-input.ts                        # toDeckRestaurant maps menu, phone
│   └── deck-input.spec.ts                   # updated
├── deck/
│   ├── deck.texts.ts                        # + back texts (Horaires, Adresse, Itinéraire, Menu, travel modes...)
│   ├── deck.component.html / .css / .ts     # logo becomes a link to '/'; flip button wiring
│   ├── deck.component.spec.ts               # updated
│   ├── format/
│   │   ├── opening.ts / .spec.ts            # + targetWeekday(); closingTimeAt reuses it
│   │   └── weekly-hours.ts / .spec.ts       # rows "Lundi 12:00 – 14:30 · 19:00 – 22:30", targeted day flagged
│   ├── swipe-card/
│   │   ├── swipe-card.component.ts / .html / .css / .spec.ts   # front + back, flip state, tap vs drag
│   │   └── route-preview/
│   │       └── route-preview.component.ts / .html / .css / .spec.ts   # travel times, button to full route
│   ├── route-preview.service.ts             # abstract contract + RoutePreview type
│   └── stub/
│       └── stub-route-preview.service.ts / .spec.ts
└── app.config.ts / app.config.spec.ts       # provide RoutePreviewService -> stub
```

**Structure Decision**: same feature-oriented layout as 003. The back lives in `swipe-card` (it is the other face of the same card), the preview in a child component, the contract next to `refinement.service.ts`, the stub in `stub/`. Feature 006 replaces only the provider.

## Requirement Traceability

| Requirement | Where |
|-------------|-------|
| FR-001 flip both ways (button, tap) | `swipe-card.component` (flip signal; tap ignored after a drag) |
| FR-002 back content | `swipe-card.component.html`, `weekly-hours.ts` |
| FR-003 no empty section | `@if` on `menu` and on non-empty weekly hours |
| FR-004 targeted day highlighted | `targetWeekday()` in `opening.ts`, `weekly-hours.ts` |
| FR-005 route preview with 4 modes | `route-preview.component`, `RoutePreviewService` |
| FR-006 preview is not a reaction | preview state is local to the card; no call to `deck.store` |
| FR-007 button to full route, return keeps the deck | `/route/:id` placeholder; return behavior already in 003 (FR-014) |
| FR-008 reaction buttons stay available | deck buttons outside the card, unchanged |
| FR-009 responsive | `swipe-card.component.css`, breakpoint 768 px as in 003 |
| Edge: mode without route hidden | `RoutePreview.modes` lists only available modes |
| Edge: position unknown | see D-04-5 |
| Edge: reaction while back shown | reaction applies to the card whatever the face; swipe is disabled on the back (D-04-3) |
| Extra: logo to home | `deck.component.html` (`routerLink="/"`) |

## Decisions to validate

Proposals made by the plan author, **not yet confirmed by the product owner**. Review before `/speckit-tasks`.

| # | Topic | Proposal |
|---|-------|----------|
| D-04-1 | Logo link | Contradicts 003 D3 ("no home link on the deck"). Product owner asked for it on 2026-11-25; 003's plan note is amended by this one. Link target `/`, no confirmation, deck state dropped (router navigation destroys the deck screen; the reactions kept by `SessionReactionsService` follow its existing lifetime, to confirm). |
| D-04-2 | Flip control | "↻" button (44 px, bottom-right of the hero, as on the mockup) on the front, and "↻" at the top of the back. Tap on the card flips too. Accessible label to validate (proposal: "Retourner la carte", the mockup's label). |
| D-04-3 | Gestures on the back | Swiping is disabled while the back is shown (as on the mockup: `down` returns when flipped) so that scrolling inside the card works (US1 AS4). The reaction buttons stay usable (FR-008). The card goes back to the front when the next card appears. |
| D-04-4 | Tap vs drag | A pointer sequence under the 6 px drag threshold of 003 (D5) is a tap; anything above is a drag and does not flip. |
| D-04-5 | Position for the preview | The contract takes the restaurant and returns the preview. The stub ignores the user's position. Requesting the device position again and the fallback to the search place (spec edge case, 006 FR-010) belong to the real service of 006, not to this feature. |
| D-04-6 | Stub travel times | Fictional values, derived from `distanceMeters` with fixed speeds (**values to validate, not business data**): foot 5 km/h, bike 15 km/h, car 30 km/h, public transport 20 km/h plus 5 min. Rounded up to the minute. One stub case without public transport to exercise the "mode hidden" rule. |
| D-04-7 | Menu display | Dishes in the order of the backend, name, description, price as given (strings, no parsing). A dish without description or price shows without that part. No category tabs (the mockup `Restaurant` screen has them; `MenuItem` has no category). |
| D-04-8 | Phone | Carried in the model for a future "Appeler" action; **not displayed** (not in the spec). |
| D-04-9 | Header of the back | Name, cuisine, distance (existing formatter), price level when present, keywords as chips. |
| D-04-10 | Day names | French full names, Monday first on the back, from `Intl.DateTimeFormat('fr-FR')` or a constant (decided in tasks; one place). |
| D-04-11 | Route button labels | "Itinéraire" (spec). Full-route button label to validate (proposal: "Voir l'itinéraire complet"). Travel mode labels: "À pied", "À vélo", "En voiture", "Transports" (proposal). |
| D-04-12 | Closed days | A day with no range shows "Fermé" (mockup). |
| D-04-13 | Rounded 6 | The spec asks for the address placeholder "[ADRESSE], Grenoble" to be real data: the back shows the `address` of the restaurant as given (already used on the front). |

### Still open (outside 004's scope)

1. The real routing service and the position request: feature 006.
2. SC-003 (80% find the hours without help): measured in acceptance testing, not by unit tests.
3. Constitution ratification date is still `TODO` (not part of this feature).

## Complexity Tracking

No constitution violation to justify.
