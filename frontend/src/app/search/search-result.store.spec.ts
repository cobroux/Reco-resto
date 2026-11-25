import { TestBed } from '@angular/core/testing';
import { STUB_DECK_INPUT } from '../deck/stub/stub-restaurants';
import { SearchResultStore } from './search-result.store';

describe('SearchResultStore', () => {
  it('holds no result before any search', () => {
    expect(TestBed.inject(SearchResultStore).result()).toBeNull();
  });

  it('keeps the last search result', () => {
    const store = TestBed.inject(SearchResultStore);
    store.set(STUB_DECK_INPUT);

    expect(store.result()).toBe(STUB_DECK_INPUT);
  });
});
