import { Injectable, Signal, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  Subscription,
  catchError,
  defaultIfEmpty,
  forkJoin,
  map,
  of,
  take,
  timer,
} from 'rxjs';
import { SearchResultStore } from '../search/search-result.store';
import { SessionReactionsService } from '../session/session-reactions.service';
import { COOKING_TOSS_MS } from './cooking-toss.token';
import { DECK_SIZE } from './deck-size.token';
import {
  DeckState,
  activeReactions,
  advance,
  applyRefinement,
  canRewind,
  confirmPick,
  createDeck,
  currentCard,
  markRefinementFailed,
  recordReaction,
  refinementRequest,
  rewind,
} from './deck-state';
import { DeckInput, ReactionKind } from './deck.types';
import { RefinementService } from './refinement.service';

/**
 * Session-wide holder of the swipe deck. Wraps the pure deck state, reads the last search result,
 * calls the refinement service, publishes reactions to the session and navigates to the next screens.
 */
@Injectable({ providedIn: 'root' })
export class DeckStore {
  private readonly results = inject(SearchResultStore);
  private readonly refinement = inject(RefinementService);
  private readonly session = inject(SessionReactionsService);
  private readonly router = inject(Router);
  private readonly deckSize = inject(DECK_SIZE);
  private readonly tossMs = inject(COOKING_TOSS_MS);

  private readonly stateValue = signal<DeckState | null>(null);
  private readonly inputValue = signal<DeckInput | null>(null);
  private readonly pendingValue = signal(false);
  private refinementCall?: Subscription;

  readonly state: Signal<DeckState | null> = this.stateValue.asReadonly();
  readonly input: Signal<DeckInput | null> = this.inputValue.asReadonly();
  /** True while waiting for a refinement result; reactions are ignored meanwhile (D2). */
  readonly pending: Signal<boolean> = this.pendingValue.asReadonly();

  /**
   * Builds the deck once per search result: a new result starts a new deck and discards the previous
   * reactions (FR-020); re-opening the same deck after a pick counts as a return (FR-014).
   */
  start(): void {
    const input = this.results.result();
    if (!input) {
      return;
    }
    const state = this.stateValue();
    if (state && this.inputValue() === input) {
      if (state.status === 'picked') {
        this.returnFromPick();
      }
      return;
    }
    this.refinementCall?.unsubscribe();
    this.pendingValue.set(false);
    this.inputValue.set(input);
    this.update(createDeck(input.restaurants, this.deckSize));
  }

  react(kind: ReactionKind): void {
    const state = this.stateValue();
    if (!state || state.status !== 'browsing' || this.pendingValue()) {
      return;
    }
    const reacted = recordReaction(state, kind);
    if (kind === 'picked') {
      this.update(reacted);
      void this.router.navigate(['/choice', currentCard(reacted)!.id]);
      return;
    }
    this.continueAfterReaction(reacted);
  }

  /** Back from the choice screen: the pick counts as liked and the deck goes on (FR-014, D12). */
  returnFromPick(): void {
    const state = this.stateValue();
    if (!state || state.status !== 'picked') {
      void this.router.navigate(['/deck']);
      return;
    }
    this.continueAfterReaction(confirmPick(state));
    if (this.stateValue()!.status !== 'finished') {
      void this.router.navigate(['/deck']);
    }
  }

  rewind(): void {
    const state = this.stateValue();
    if (!state || !canRewind(state) || this.pendingValue()) {
      return;
    }
    const rewound = rewind(state);
    this.update(rewound);
    this.refineThen(rewound, (next) => next);
  }

  private continueAfterReaction(reacted: DeckState): void {
    if (reacted.planned.length === 0) {
      this.finish(advance(reacted));
      return;
    }
    this.update(reacted);
    this.refineThen(reacted, advance, this.tossMs);
  }

  /**
   * Calls the refinement and applies `then` once it answered or failed (FR-006, FR-013), and not
   * before `minWaitMs` (one crêpe toss after a reaction, FR-018).
   */
  private refineThen(base: DeckState, then: (state: DeckState) => DeckState, minWaitMs = 0): void {
    this.refinementCall?.unsubscribe();
    this.pendingValue.set(true);
    const failed = markRefinementFailed(base);
    const refined: Observable<DeckState> = this.refinement.refine(refinementRequest(base)).pipe(
      take(1),
      map((result) => applyRefinement(base, result)),
      catchError(() => of(failed)),
      defaultIfEmpty(failed),
    );
    const ready =
      minWaitMs > 0 ? forkJoin([refined, timer(minWaitMs)]).pipe(map(([state]) => state)) : refined;
    this.refinementCall = ready.subscribe((state) => this.finish(then(state)));
  }

  private finish(state: DeckState): void {
    this.pendingValue.set(false);
    this.update(state);
    if (state.status === 'finished') {
      void this.router.navigate(['/results']);
    }
  }

  private update(state: DeckState): void {
    this.stateValue.set(state);
    this.session.set(state.candidates, activeReactions(state));
  }
}
