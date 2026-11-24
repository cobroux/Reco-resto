import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { DeckSource } from '../deck-source';
import { DeckInput } from '../deck.types';
import { STUB_DECK_INPUT } from './stub-restaurants';

/** Temporary deck source, replaced by feature 002. */
@Injectable()
export class StubDeckSource extends DeckSource {
  override load(): Observable<DeckInput> {
    return of(STUB_DECK_INPUT);
  }
}
