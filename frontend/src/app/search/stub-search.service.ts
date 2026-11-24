import { Injectable } from '@angular/core';
import { map, Observable, timer } from 'rxjs';
import { SearchService } from './search.service';

/**
 * Temporary stand-in for feature 002 (natural-language search).
 * The 500 ms delay is an unverified placeholder; replace this provider when feature 002 lands.
 */
@Injectable()
export class StubSearchService extends SearchService {
  search(_sentence: string): Observable<void> {
    return timer(500).pipe(map(() => undefined));
  }
}
