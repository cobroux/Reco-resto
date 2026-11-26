# Research: Restaurant Card Details

Date: 2026-11-25. No external research was needed; findings come from the code base.

## R1. Where do menu and phone come from?

- **Finding**: `backend/.../search/restaurant/Restaurant.java` already has `menu` (`List<MenuItem>`) and `phone`. `MenuItem` is `(String name, String description, String price)`, all optional (`NON_NULL`). `frontend/src/app/search/search-result.ts` types `phone` but not `menu`; `deck-input.ts` maps neither into the deck `Restaurant`.
- **Decision**: extend the frontend types and `toDeckRestaurant`. No backend change.
- **Alternatives**: stub-only menu (rejected by the product owner).

## R2. Route preview source

- **Finding**: no routing code exists; feature 006 is not built; `/route/:id` is a placeholder. The spec leaves the routing service to planning and says travel times reuse 006's computation.
- **Decision**: abstract `RoutePreviewService` + stub (product owner). Same pattern as `RefinementService` / `StubRefinementService` / `app.config.ts`.
- **Alternatives**: a real routing API now (rejected: larger scope, belongs to 006).

## R3. Targeted day

- **Finding**: `closingTimeAt` in `deck/format/opening.ts` already derives the weekday from `targetDateTime` (ISO read as written). `weeklyHours` is indexed Sunday = 0.
- **Decision**: extract the weekday computation as one exported helper and reuse it (DRY); the back highlights `targetWeekday(targetDateTime)`.

## R4. Flip and swipe coexistence

- **Finding**: `SwipeCardComponent` handles pointer events on the whole `article` and flies the card off on a swipe; the mockup disables dragging when flipped and ignores the click that ends a drag.
- **Decision**: flip state in the card; swipe disabled on the back; a sequence under the 6 px drag threshold counts as a tap.

## R5. Logo

- **Finding**: the deck header shows `<span class="logo">` with the text from `DECK_TEXTS.logo` ("Nappe"). The user wrote "Tablée" in the request; the app and spec 003 use "Nappe". The mockup uses "Tablée".
- **Decision**: keep the app's "Nappe"; make the logo a link to `/`. `RouterLink` is available (`@angular/router` installed).
