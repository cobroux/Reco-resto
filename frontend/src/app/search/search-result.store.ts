import { Injectable, Signal, signal } from '@angular/core';
import { DeckInput } from '../deck/deck.types';

/**
 * Result of the last successful search of the session, read by the deck (spec 003 FR-019–FR-021).
 * In memory only: lost on page reload.
 */
@Injectable({ providedIn: 'root' })
export class SearchResultStore {
  private readonly resultState = signal<DeckInput | null>(null);

  /** Null until a search succeeded in this session. */
  readonly result: Signal<DeckInput | null> = this.resultState.asReadonly();

  set(result: DeckInput): void {
    this.resultState.set(result);
  }
}
