import { Injectable } from '@angular/core';
import { map, Observable, timer } from 'rxjs';
import { DeckInput } from '../deck/deck.types';
import { STUB_DECK_INPUT } from '../deck/stub/stub-restaurants';
import { SearchService } from './search.service';

/**
 * Temporary stand-in for feature 002 (natural-language search): returns the fixed stub deck input.
 * The 500 ms delay is an unverified placeholder; replace this provider when feature 002 lands.
 */
@Injectable()
export class StubSearchService extends SearchService {
  search(_sentence: string): Observable<DeckInput> {
    return timer(500).pipe(map(() => STUB_DECK_INPUT));
  }
}
