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

  describe('replace', () => {
    const NEXT = { ...STUB_DECK_INPUT };

    it('swaps the current result for its updated version and remembers the one it replaced', () => {
      const store = TestBed.inject(SearchResultStore);
      store.set(STUB_DECK_INPUT);

      expect(store.replace(STUB_DECK_INPUT, NEXT)).toBe(true);

      expect(store.result()).toBe(NEXT);
      expect(store.predecessorOf(NEXT)).toBe(STUB_DECK_INPUT);
    });

    it('ignores an update of a result that a newer search has already replaced', () => {
      const store = TestBed.inject(SearchResultStore);
      const newer = { ...STUB_DECK_INPUT };
      store.set(STUB_DECK_INPUT);
      store.set(newer);

      expect(store.replace(STUB_DECK_INPUT, NEXT)).toBe(false);

      expect(store.result()).toBe(newer);
      expect(store.predecessorOf(NEXT)).toBeUndefined();
    });
  });
});
