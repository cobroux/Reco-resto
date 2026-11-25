# Contract: Home Search Screen

## 1. Route

| Path | Component | Notes |
|------|-----------|-------|
| `''` | `HomeComponent` | Entry point of the application. |

## 2. Search service (boundary with feature 002)

```text
abstract class SearchService
  search(sentence: string): Observable<DeckInput>
```

- Receives the sentence exactly as typed (FR-005).
- Emits the `DeckInput` of the search once (shape: `specs/003-swipe-deck/data-model.md`) and completes; errors when the search fails. Per-case error messages belong to feature 002.
- On success, `HomeComponent` stores the result in `SearchResultStore` and opens `/deck`; on error it stays on the home screen and shows `HOME_TEXTS.searchError` (spec 003 FR-019, amendment 2026-11-25 (b)).
- `StubSearchService` is the temporary provider (returns the 003 stub deck input after 500 ms). Feature 002 replaces the provider; `HomeComponent` does not change.

## 3. Screen contract (what tests assert)

| Id | Behavior | Source |
|----|----------|--------|
| U1 | Displays exactly: logo "Nappe", badge "Restaurants autour de vous", title "Où manger, juste à côté ?", subtitle "Trouvez un restaurant adapté à votre régime et à vos envies, en quelques secondes.", placeholder "Dites-nous ce qui vous ferait plaisir, et où", button "Commencer" | FR-001 |
| U2 | Displays four tiles "Vegan", "Végétarien", "Sans gluten", "Halal, casher…"; clicking them changes nothing | FR-007 |
| U3 | No element labelled "Se connecter" | FR-008 |
| U4 | Click on "Commencer" or Enter in the field calls `SearchService.search` once with the unchanged sentence | FR-003, FR-005 |
| U5 | Empty or space-only sentence: no call, message "Votre recherche est vide. Ex. : asiatique végétarien près d'Opéra" | FR-004 |
| U6 | While searching: in-progress indication, button disabled, a second submission does not call the service | FR-006 |
| U7 | Field is empty when the screen is opened | Edge case |
