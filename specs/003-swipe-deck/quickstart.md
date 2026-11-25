# Quickstart: Swipe Deck

How to check that feature 003 works. Contracts: [contracts/](./contracts/); model: [data-model.md](./data-model.md).

## Prerequisites

- Node.js (current LTS or newer) and npm.
- `npm --prefix frontend install`

## Automated tests

```bash
npm --prefix frontend test -- --watch=false
```

Expected: all tests pass, including deck state, gesture classifier, formatters, stubs, store and components.

## Run

```bash
npm --prefix frontend start
```

Open http://localhost:4200/deck (D14). The stub data set is used.

## Manual scenarios

Check each at 1280 px (mouse) and 375 px (device emulation, touch).

| # | Story | Steps | Expected |
|---|-------|-------|----------|
| 1 | US1 | Open the deck | Title "Ça vous dit, ce soir ?", progress "1 sur 15", card with photo or tablecloth, distance, name, chips, price, "Ouvert jusqu'à …", "À goûter" |
| 2 | US1 | Browse to a stub restaurant without photo / without dish | Tablecloth visual / default dish visual without name |
| 3 | US2 | Press "✕", then "♥"; drag left, then right | Next card each time; reactions visible on `/results` |
| 4 | US2 | Press "→" or swipe up | Route placeholder with the restaurant name and a button to results |
| 5 | US2 | Press "Voir la liste" mid-deck | Results placeholder with reactions so far |
| 6 | US3 | React to several cards | Progress total stays 15; no restaurant appears twice |
| 7 | US3 | Make the stub refinement fail (test switch) and react | Next planned card shown, message "Suggestions non personnalisées pour le moment" |
| 8 | US4 | React to 3 cards, press "<" three times | Cards 3, 2, 1 come back; reactions removed; "<" disabled on card 1 |
| 9 | US5 | React to all 15 cards | Results placeholder opens |
| 10 | FR-014 | Pick, then go back to the deck | Next card shown; picked restaurant listed as liked |
| 11 | D12 | Pick the last card, then go back | Results placeholder opens; picked restaurant listed as liked |
| 12 | D7 | React to cards 1–5, rewind to card 3 | Cards 4 and 5 may come back later in the deck; reacted cards never do |
| 13 | FR-012 | Desktop layout | No "Vos coups de cœur" panel |

Not checkable with the stub: SC-001 (deferred to 002), SC-004 (needs real refinement).
