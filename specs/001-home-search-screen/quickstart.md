# Quickstart: validate the Home Search Screen

## Prerequisites

- Node and npm available; `npm --prefix frontend install` done.

## Automated checks

```text
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
```

Expected: all tests pass (contract items U1 to U7 in [contracts/ui-and-service-contract.md](./contracts/ui-and-service-contract.md)); the build succeeds.

## Manual checks

Start with `npm --prefix frontend start`, open the printed local URL.

1. **Desktop, 1280 px wide**: compare with the mockup board `V2WebAccueil`. All texts match FR-001 and FR-007 (except "Nappe" and the spec overrides listed in `plan.md`). No horizontal scroll (SC-002).
2. **Mobile, 375 px wide** (browser device mode): compare with `V2MobileAccueil`. Stacked layout, field and button below the tiles, no horizontal scroll (SC-002).
3. Type a sentence and press "Commencer", then repeat with Enter: the in-progress state shows; a second press does nothing.
4. Press "Commencer" with an empty field and with spaces only: the message "Votre recherche est vide. Ex. : asiatique végétarien près d'Opéra" shows and nothing is submitted (SC-003).
5. Click each tile: nothing happens. No "Se connecter" button is visible.
