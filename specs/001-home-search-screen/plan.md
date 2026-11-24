# Implementation Plan: Home Search Screen

**Branch**: `feat/home-search-screen` (current local branch is `test-on-home-search-screen`, see Open points) | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-home-search-screen/spec.md`

## Summary

Build the Nappe home screen in the Angular frontend: logo, badge, title, subtitle, one free-text search field with a "Commencer" button, and four decorative dietary tiles. The visual design follows the Claude Design mockup (boards `V2WebAccueil` for desktop and `V2MobileAccueil` for mobile); wherever the mockup and the spec differ, the spec wins. The submitted sentence goes, unchanged, through an injectable `SearchService` whose implementation is a stub until feature 002 replaces it. The screen validates the sentence, shows an in-progress state and blocks double submission. No backend work.

## Technical Context

**Language/Version**: TypeScript 6.0, Angular 22.3 (standalone components, signals, new control flow)

**Primary Dependencies**: `@angular/core`, `@angular/router`, `@angular/forms` (already installed); Google Fonts "Outfit" 400 to 700 (from the mockup). No new npm dependency.

**Storage**: N/A

**Testing**: Vitest 5 with jsdom through `ng test` (`npm --prefix frontend test -- --watch=false`); tests next to the code as `*.spec.ts`.

**Target Platform**: Web browsers, desktop (test width 1280 px) and mobile (test width 375 px)

**Project Type**: Web application, frontend part only

**Performance Goals**: None specified in the spec. Not invented.

**Constraints**: French UI (FR-010); no horizontal scroll at 1280 px and 375 px (SC-002); texts word for word as in FR-001 and FR-007.

**Scale/Scope**: One screen, one route, one service.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | How the plan complies |
|-----------|--------|----------------------|
| I. Spec-Driven Development | Pass | Plan follows the approved-spec workflow; the plan itself needs approval from a teammate other than its author before `/speckit-tasks`. |
| II. Test-First | Pass | Tasks must put each `*.spec.ts` before the code it covers. |
| III. Never Guess | Pass with flags | Four values are not defined anywhere; they are listed under Open points, not assumed. |
| IV. DRY | Pass | Repo searched: nothing to reuse (empty `app.routes.ts`, empty `app.css`, placeholder template). The placeholder in `app.html` and its test in `app.spec.ts` are replaced, not kept. All user-facing texts live in one constants file used by both the template and the tests. |
| V. English and Conventional Commits | Pass | Code and docs in English; only the UI texts are French, as the spec requires. |

## Project Structure

### Documentation (this feature)

```text
specs/001-home-search-screen/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ui-and-service-contract.md
└── tasks.md             # created later by /speckit-tasks
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── index.html                              # lang="fr", Outfit font link, title "Nappe"
│   ├── styles.css                              # global reset and design tokens (CSS variables)
│   └── app/
│       ├── app.ts / app.html / app.css         # placeholder removed; shell = <router-outlet /> only
│       ├── app.spec.ts                         # updated: shell creates, no placeholder title
│       ├── app.routes.ts                       # '' -> HomeComponent
│       ├── search/
│       │   ├── search.service.ts               # abstract SearchService (injection token / contract)
│       │   ├── stub-search.service.ts          # temporary implementation, replaced by feature 002
│       │   └── stub-search.service.spec.ts
│       └── home/
│           ├── home.component.ts / .html / .css
│           ├── home.component.spec.ts          # behavior, texts, no "Se connecter", tiles inert
│           ├── home.texts.ts                   # single source of every French text (FR-001, FR-004, FR-007)
│           └── diet-tiles/ (optional split)    # only if the template gets too long; decided in tasks
```

**Structure Decision**: feature-oriented folders inside `frontend/src/app/` (`home/`, `search/`). The search contract is separated from the stub so that feature 002 replaces only the provider, not the screen (FR-005).

## Design Mapping (mockup to implementation)

Source: `V2WebAccueil.dc.html` (1440 px) and `V2MobileAccueil.dc.html` (390 px). Values below are read from the mockup and are the visual reference.

| Item | Desktop | Mobile |
|------|---------|--------|
| Font | Outfit 400 to 700 | same |
| Page background | `#F5F3FC` | same |
| Hero panel | `#E9E5FD`, radius 40 px, padding 64 px, max width 1280 px | radius 32 px, padding 28/22 px |
| Primary / text / muted / placeholder | `#5B4BC4` / `#23202E` / `#4E4960` / `#6A6578` | same |
| Title | 60 px, bold, line-height 1.05, two lines | 40 px |
| Layout | 2 columns: text + pill search form, then 2x2 tiles (second column offset 32 px) | stacked: panel with text and tiles, then field and button below, 56 px high |
| Tiles | Vegan `#E3F5E1` / `#2F7A3A`, Végétarien `#FDEBDD` / `#A24E12`, Sans gluten `#FCE4EA` / `#B0284A`, Halal, casher… `#FFFFFF` / `#5B4BC4` | same, smaller |

Spec overrides applied on top of the mockup:

1. No "Se connecter" button (FR-008).
2. Logo text "Nappe" instead of "Tablée".
3. Placeholder "Dites-nous ce qui vous ferait plaisir, et où".
4. Subtitle "Trouvez un restaurant adapté à votre régime et à vos envies, en quelques secondes." on both layouts (the mobile mockup drops "Trouvez").
5. The mockup's Emocio design system (Quicksand, green) is not used; the boards actually use Outfit and violet, and the product owner chose the boards.

## Open points to validate (not assumed)

1. **Maximum sentence length**: the spec defers it to planning "with feature 002". No value exists. Until decided, the field has no limit. Needs an answer from the team or feature 002.
2. **Stub duration**: the stub needs a simulated delay to make the in-progress state visible. The plan proposes a placeholder of 500 ms, unverified and temporary; confirm or give another value.
3. **Behavior when the stub completes**: no destination screen exists yet. The plan proposes: the in-progress state ends, the field keeps its text, nothing else changes. Confirm.
4. **Trimming**: FR-005 says the sentence is handed over "unchanged", FR-004 rejects space-only input. The plan proposes to test emptiness on the trimmed value but to submit the original untrimmed text. Confirm.
5. **Branch**: local branch is `test-on-home-search-screen`; the spec and constitution expect `feat/home-search-screen`. Rename or create before committing.
6. **Constitution ratification date** is still `TODO` (not part of this feature).

## Complexity Tracking

No constitution violation to justify.
