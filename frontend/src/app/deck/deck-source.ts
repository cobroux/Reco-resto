import { Observable } from 'rxjs';
import { DeckInput } from './deck.types';

/**
 * Boundary between the search (feature 002) and the swipe deck.
 * Feature 002 replaces the stub provider with the real search result.
 */
export abstract class DeckSource {
  /** Deck input of the current search. */
  abstract load(): Observable<DeckInput>;
}
