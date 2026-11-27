# Service Contracts: Swipe Deck

Frontend contracts between the deck (003), the search (002) and the next screens (005, 006). Types refer to [data-model.md](../data-model.md). TypeScript signatures are the contract; bodies belong to the implementation.

## SearchService and SearchResultStore (search provided by 002, stubbed; reconciled with 001)

```ts
export abstract class SearchService {          // app/search/search.service.ts (001 contract, extended)
  /** Emits the deck input of the search once, then completes; errors when the search fails. */
  abstract search(sentence: string): Observable<DeckInput>;
}

@Injectable({ providedIn: 'root' })
export class SearchResultStore {               // app/search/search-result.store.ts
  readonly result: Signal<DeckInput | null>;   // null until a search succeeded in the session
  set(result: DeckInput): void;
}
```

- The home screen (001) calls `SearchService`, stores the result and opens `/deck` (FR-019). The deck reads `SearchResultStore`; it never calls the search.
- `/deck` is guarded by `searchResultGuard`: no result in the session opens the home screen (FR-021).
- `restaurants` are ranked, open at the targeted moment, with a known address (002 FR-007, FR-016).
- Stub: `StubSearchService` returns, after 500 ms, the fixed data set of 16 fictional Paris restaurants (D6), `momentLabel: 'ce soir'` and a fixed `targetDateTime`. Feature 002 replaces this single provider.

## RefinementService (provided by 002, stubbed in 003)

```ts
export interface RefinementRequest {
  candidates: Restaurant[];      // pool of the deck input
  reactions: Reaction[];         // active reactions only, in order
  excludedIds: string[];         // restaurants that must not come back (rule R1)
  slots: number;                 // number of cards still to plan
}

export abstract class RefinementService {
  /** Next planned cards, most relevant first. May error; the deck then keeps its plan (FR-013). */
  abstract refine(request: RefinementRequest): Observable<Restaurant[]>;
}
```

- The deck enforces R1–R3 on the result whatever the implementation returns.
- Stub: `StubRefinementService` returns the candidates not excluded, in their initial order, truncated to `slots`. It has a test-only failure mode.

## DECK_SIZE

```ts
export const DECK_SIZE = new InjectionToken<number>('DECK_SIZE', { factory: () => 15 });
```

- Default 15 (FR-002). Temporary single source on the frontend until 002 owns it; then the token is removed (D17).

`DeckInput.targetDateTime` (D8) is part of this contract; feature 002 must confirm it in its plan.

## SessionReactionsService (read by 005 and 006)

```ts
@Injectable({ providedIn: 'root' })
export class SessionReactionsService {
  readonly restaurants: Signal<Restaurant[]>;
  readonly reactions: Signal<Reaction[]>;   // active reactions only
  restaurant(id: string): Restaurant | undefined;
}
```

- Written only by `DeckStore`. In memory; reset on page reload.

## DeckStore (used by the deck and the route placeholder)

```ts
@Injectable({ providedIn: 'root' })
export class DeckStore {
  readonly state: Signal<DeckState | null>;
  start(): void;                       // builds the deck from SearchResultStore; a new result starts a new deck (FR-020)
  react(kind: 'liked' | 'disliked' | 'picked'): void;
  rewind(): void;
  returnFromPick(): void;              // FR-014
}
```
