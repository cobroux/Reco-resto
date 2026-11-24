# Research: Home Search Screen

No `NEEDS CLARIFICATION` remains in the Technical Context. Open business values are tracked in `plan.md`, section "Open points to validate".

## Decisions

### D1 - Component model
- **Decision**: one standalone `HomeComponent` routed at `''`; the root `App` only hosts `<router-outlet />`.
- **Rationale**: the placeholder content of `app.html` must go; later features (swipe deck, map) are other routes.
- **Alternatives**: keep the screen inside `App` (rejected: blocks routing for features 003 to 006).

### D2 - Form handling
- **Decision**: a native `<form>` with a signal-backed text value (`FormControl` from `@angular/forms` is acceptable); the Enter key works through the form `submit` event, so FR-003 needs no keyboard code.
- **Rationale**: least code, accessible by default.
- **Alternatives**: `keydown.enter` handler (rejected: duplicates native behavior).

### D3 - Search contract
- **Decision**: abstract `SearchService` with `search(sentence: string): Observable<void>`, provided at root by `StubSearchService`. Feature 002 swaps the provider.
- **Rationale**: FR-005 requires handing the sentence over unchanged without knowing the real search; DRY, single entry point.
- **Alternatives**: call a backend endpoint now (rejected by the product decision: stub only).

### D4 - In-progress state and double submission
- **Decision**: a `searching` signal in `HomeComponent`; the submit handler returns early when it is true, and the button is `disabled` with an accessible label while it runs.
- **Rationale**: covers FR-006 in code, not only in the UI.

### D5 - Texts
- **Decision**: all French texts in `home.texts.ts`, imported by the template and by the spec file.
- **Rationale**: DRY and SC-001 (word-for-word match); one place to change.

### D6 - Styling
- **Decision**: plain CSS with CSS variables for the mockup palette, defined in `styles.css`; Outfit loaded from Google Fonts in `index.html`. Responsive through one media query (mobile layout under 768 px; breakpoint value to confirm, see below) and flex/grid wrap.
- **Rationale**: no new dependency; matches the mockup values exactly.
- **Alternatives**: component library or Tailwind (rejected: not in the stack).
- **Unverified**: the mockup shows 1440 px and 390 px only; the exact breakpoint between them is not specified. 768 px is a proposal to confirm.

### D7 - Accessibility
- **Decision**: real `<form>`, `<input>` with an `aria-label` equal to the placeholder, real `<button type="submit">`; tiles are non-focusable `<div>`s (decorative, FR-007); icons `aria-hidden`; text contrast taken from the mockup, to be checked.

### D8 - Tests (Vitest + jsdom)
- Component tests cover behavior (FR-003 to FR-008), texts (FR-001, FR-007, SC-001), tiles inert, no "Se connecter".
- jsdom does not lay out CSS: SC-002 (no horizontal scroll at 1280 and 375) is verified manually with the quickstart, not by an automated test. This limit is stated, not hidden.
