import { Observable } from 'rxjs';
import { DeckInput } from '../deck/deck.types';

/**
 * Boundary between the home screen and the natural-language search (feature 002).
 * Receives the sentence exactly as typed, emits the deck input of the search as soon as it is known, then, optionally, an updated
 * version of the same input (same restaurants, same order, e.g. with walking distances), then completes;
 * errors when the search fails.
 */
export abstract class SearchService {
  abstract search(sentence: string): Observable<DeckInput>;
}
