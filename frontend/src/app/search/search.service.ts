import { Observable } from 'rxjs';

/**
 * Boundary between the home screen and the natural-language search (feature 002).
 * Receives the sentence exactly as typed and completes when the search ends.
 */
export abstract class SearchService {
  abstract search(sentence: string): Observable<void>;
}
