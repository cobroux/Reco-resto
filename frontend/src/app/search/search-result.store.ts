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

  /** Updated results (same search) → the result they replace, so the deck can keep its progress. */
  private readonly predecessors = new WeakMap<DeckInput, DeckInput>();

  set(result: DeckInput): void {
    this.resultState.set(result);
  }

  /**
   * Replaces `previous` by an updated version of the same search (e.g. with walking distances). Ignored when a
   * newer search has replaced `previous` meanwhile. Returns whether the result was replaced.
   */
  replace(previous: DeckInput, next: DeckInput): boolean {
    if (this.resultState() !== previous) {
      return false;
    }
    this.predecessors.set(next, previous);
    this.resultState.set(next);
    return true;
  }

  /** The result that `result` replaced through `replace`, if any. */
  predecessorOf(result: DeckInput): DeckInput | undefined {
    return this.predecessors.get(result);
  }
}
