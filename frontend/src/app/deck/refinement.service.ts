import { Observable } from 'rxjs';
import { Restaurant } from '../restaurant/restaurant';
import { Reaction } from './deck.types';

export interface RefinementRequest {
  /** Pool of the deck input. */
  readonly candidates: readonly Restaurant[];
  /** Active reactions only, in deck order. */
  readonly reactions: readonly Reaction[];
  /** Restaurants that must not come back (cards currently shown in the deck). */
  readonly excludedIds: readonly string[];
  /** Number of cards still to plan. */
  readonly slots: number;
}

/**
 * Recomputes the cards still to come from the user's reactions (feature 002's language model).
 * The deck enforces its own rules on the result, whatever the implementation returns.
 */
export abstract class RefinementService {
  /** Next planned cards, most relevant first. May error: the deck then keeps its plan (FR-013). */
  abstract refine(request: RefinementRequest): Observable<readonly Restaurant[]>;
}
