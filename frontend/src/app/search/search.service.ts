import { Observable } from 'rxjs';
import { SearchResult } from './search-result';

/**
 * Boundary between the home screen and the natural-language search (feature 002).
 * Receives the sentence exactly as typed, emits the result and completes when the search ends; a failure is a
 * `SearchFailure` carrying the error code of the backend.
 */
export abstract class SearchService {
  abstract search(sentence: string): Observable<SearchResult>;
}
