import { Observable } from 'rxjs';
import { DeckInput } from '../deck/deck.types';

/**
 * Boundary between the home screen and the natural-language search (feature 002).
 * Receives the sentence exactly as typed, emits the deck input of the search once and completes;
 * errors when the search fails.
 */
export abstract class SearchService {
  abstract search(sentence: string): Observable<DeckInput>;
}
