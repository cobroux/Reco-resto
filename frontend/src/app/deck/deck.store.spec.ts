import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';
import { vi } from 'vitest';
import { Restaurant } from '../restaurant/restaurant';
import { SearchResultStore } from '../search/search-result.store';
import { SessionReactionsService } from '../session/session-reactions.service';
import { COOKING_TOSS_MS } from './cooking-toss.token';
import { DECK_SIZE } from './deck-size.token';
import { currentCard, position } from './deck-state';
import { DeckStore } from './deck.store';
import { DeckInput } from './deck.types';
import { RefinementRequest, RefinementService } from './refinement.service';

function restaurants(count: number): Restaurant[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `r${i + 1}`,
    name: `Restaurant ${i + 1}`,
    distanceMeters: 100,
    cuisine: 'Italienne',
    keywords: [],
    weeklyHours: [],
  }));
}

describe('DeckStore', () => {
  let store: DeckStore;
  let session: SessionReactionsService;
  let navigate: ReturnType<typeof vi.spyOn>;
  let pending: Subject<readonly Restaurant[]>[];
  let refine: ReturnType<typeof vi.fn<(r: RefinementRequest) => Observable<readonly Restaurant[]>>>;
  let all: Restaurant[];
  let results: SearchResultStore;

  function setUp(count: number, mode: 'instant' | 'manual' = 'instant', tossMs = 0): void {
    all = restaurants(count);
    const input: DeckInput = {
      momentLabel: 'ce soir',
      targetDateTime: '2026-11-27T20:00',
      restaurants: all,
    };
    pending = [];
    refine = vi.fn((request: RefinementRequest) => {
      if (mode === 'instant') {
        const excluded = new Set(request.excludedIds);
        return of(all.filter((r) => !excluded.has(r.id)).slice(0, request.slots));
      }
      const subject = new Subject<readonly Restaurant[]>();
      pending.push(subject);
      return subject.asObservable();
    });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RefinementService, useValue: { refine } },
        { provide: DECK_SIZE, useValue: 3 },
        { provide: COOKING_TOSS_MS, useValue: tossMs },
      ],
    });
    store = TestBed.inject(DeckStore);
    session = TestBed.inject(SessionReactionsService);
    results = TestBed.inject(SearchResultStore);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    results.set(input);
    store.start();
  }

  /** A second search result, with restaurants distinct from the first one. */
  function newSearch(): DeckInput {
    const next: DeckInput = {
      momentLabel: 'samedi midi',
      targetDateTime: '2026-11-28T12:00',
      restaurants: restaurants(4).map((r) => ({ ...r, id: `n${r.id}` })),
    };
    results.set(next);
    return next;
  }

  const current = () => currentCard(store.state()!)?.id;

  describe('start (US1)', () => {
    it('builds the deck from the search result with the configured size', () => {
      setUp(5);

      expect(store.state()!.size).toBe(3);
      expect(current()).toBe('r1');
      expect(store.input()?.momentLabel).toBe('ce soir');
      expect(session.restaurants()).toEqual(all);
    });

    it('does not rebuild a deck already started', () => {
      setUp(5);
      store.react('liked');
      store.start();

      expect(current()).toBe('r2');
    });
  });

  describe('new search (FR-020)', () => {
    it('T3: starts a new deck from the new result and discards the previous reactions', () => {
      setUp(5);
      store.react('disliked');
      store.react('liked');
      expect(position(store.state()!)).toBe(3);

      const next = newSearch();
      store.start();

      expect(store.input()).toBe(next);
      expect(position(store.state()!)).toBe(1);
      expect(current()).toBe('nr1');
      expect(store.state()!.status).toBe('browsing');
      expect(session.restaurants()).toEqual(next.restaurants);
      expect(session.reactions()).toEqual([]);
    });

    it('T3: drops a refinement still running for the previous deck', () => {
      setUp(5, 'manual');
      store.react('liked');
      expect(store.pending()).toBe(true);

      newSearch();
      store.start();
      expect(store.pending()).toBe(false);

      pending[0].next(all.slice(2));
      pending[0].complete();
      expect(current()).toBe('nr1');
    });

    it('T3: starts a new deck after a pick of the previous deck', () => {
      setUp(5);
      store.react('picked');

      newSearch();
      store.start();

      expect(store.state()!.status).toBe('browsing');
      expect(current()).toBe('nr1');
      expect(session.reactions()).toEqual([]);
    });
  });

  describe('reactions (US2)', () => {
    it('records the reaction in the session and shows the next card', () => {
      setUp(5);
      store.react('disliked');

      expect(current()).toBe('r2');
      expect(session.reactions()).toEqual([{ restaurantId: 'r1', kind: 'disliked' }]);
    });

    it('opens the route screen on a pick (FR-008)', () => {
      setUp(5);
      store.react('picked');

      expect(navigate).toHaveBeenCalledWith(['/route', 'r1']);
      expect(session.reactions()).toEqual([{ restaurantId: 'r1', kind: 'picked' }]);
    });

    it('goes on at the next card with the pick counted as liked on return (FR-014)', () => {
      setUp(5);
      store.react('picked');
      store.returnFromPick();

      expect(current()).toBe('r2');
      expect(session.reactions()).toEqual([{ restaurantId: 'r1', kind: 'liked' }]);
      expect(navigate).toHaveBeenLastCalledWith(['/deck']);
    });

    it('goes back to the deck when there is no pick to confirm (e.g. after a reload)', () => {
      setUp(5);
      store.returnFromPick();

      expect(current()).toBe('r1');
      expect(navigate).toHaveBeenLastCalledWith(['/deck']);
    });

    it('treats a deck re-opened after a pick as a return from the route screen', () => {
      setUp(5);
      store.react('picked');
      store.start();

      expect(current()).toBe('r2');
    });
  });

  describe('refinement (US3)', () => {
    it('asks for refinement with all reactions after each reaction (AS1)', () => {
      setUp(5);
      store.react('liked');
      store.react('disliked');

      expect(refine).toHaveBeenLastCalledWith({
        candidates: all,
        reactions: [
          { restaurantId: 'r1', kind: 'liked' },
          { restaurantId: 'r2', kind: 'disliked' },
        ],
        excludedIds: ['r1', 'r2'],
        slots: 1,
      });
    });

    it('shows the next card only once the refinement answered (D2)', () => {
      setUp(5, 'manual');
      store.react('liked');

      expect(store.pending()).toBe(true);
      expect(current()).toBe('r1');

      pending[0].next([all[4], all[3]]);

      expect(store.pending()).toBe(false);
      expect(current()).toBe('r5');
      expect(position(store.state()!)).toBe(2);
    });

    it('ignores reactions while a refinement is pending', () => {
      setUp(5, 'manual');
      store.react('liked');
      store.react('liked');

      expect(refine).toHaveBeenCalledTimes(1);
    });

    it('goes on with the planned card and flags the failure, then recovers (FR-013)', () => {
      setUp(5, 'manual');
      store.react('liked');
      pending[0].error(new Error('down'));

      expect(current()).toBe('r2');
      expect(store.state()!.refinementDegraded).toBe(true);
      expect(store.pending()).toBe(false);

      store.react('liked');
      pending[1].next([all[4]]);

      expect(store.state()!.refinementDegraded).toBe(false);
      expect(current()).toBe('r5');
    });

    it('treats a refinement that completes without result as a failure', () => {
      setUp(5, 'manual');
      store.react('liked');
      pending[0].complete();

      expect(current()).toBe('r2');
      expect(store.state()!.refinementDegraded).toBe(true);
    });
  });

  describe('crêpe toss before the next card (AS8, FR-018, D21)', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('waits for one full toss when the refinement answers at once', () => {
      setUp(5, 'instant', 1000);
      store.react('liked');

      vi.advanceTimersByTime(999);
      expect(current()).toBe('r1');
      expect(store.pending()).toBe(true);
      vi.advanceTimersByTime(1);
      expect(current()).toBe('r2');
      expect(store.pending()).toBe(false);
    });

    it('waits for the refinement when it is slower than one toss', () => {
      setUp(5, 'manual', 1000);
      store.react('liked');

      vi.advanceTimersByTime(1500);
      expect(current()).toBe('r1');
      pending[0].next([]);
      expect(current()).toBe('r2');
    });

    it('waits for one toss when the refinement fails at once (FR-013)', () => {
      setUp(5, 'manual', 1000);
      store.react('liked');
      pending[0].error(new Error('down'));

      expect(current()).toBe('r1');
      vi.advanceTimersByTime(1000);
      expect(current()).toBe('r2');
      expect(store.state()!.refinementDegraded).toBe(true);
    });

    it('does not delay a rewind', () => {
      setUp(5, 'instant', 1000);
      store.react('liked');
      vi.advanceTimersByTime(1000);
      store.rewind();
      vi.advanceTimersByTime(0);

      expect(current()).toBe('r1');
      expect(store.pending()).toBe(false);
    });
  });

  describe('rewind (US4)', () => {
    it('shows the previous card, cancels its reaction and refines without it (AS1, AS4)', () => {
      setUp(5);
      store.react('liked');
      store.react('disliked');
      store.rewind();

      expect(current()).toBe('r2');
      expect(session.reactions()).toEqual([{ restaurantId: 'r1', kind: 'liked' }]);
      expect(refine).toHaveBeenLastCalledWith(
        expect.objectContaining({
          reactions: [{ restaurantId: 'r1', kind: 'liked' }],
          excludedIds: ['r1', 'r2'],
          slots: 1,
        }),
      );
    });

    it('does nothing on the first card (AS3)', () => {
      setUp(5);
      store.rewind();

      expect(current()).toBe('r1');
      expect(refine).not.toHaveBeenCalled();
    });
  });

  describe('end of deck (US5)', () => {
    it('opens the results screen after the last card (AS1)', () => {
      setUp(5);
      store.react('disliked');
      store.react('disliked');
      store.react('disliked');

      expect(store.state()!.status).toBe('finished');
      expect(navigate).toHaveBeenLastCalledWith(['/results']);
    });

    it('opens the results screen when returning from a pick on the last card (D12)', () => {
      setUp(2);
      store.react('liked');
      store.react('picked');
      store.returnFromPick();

      expect(store.state()!.status).toBe('finished');
      expect(navigate).toHaveBeenLastCalledWith(['/results']);
      expect(session.reactions()).toContainEqual({ restaurantId: 'r2', kind: 'liked' });
    });
  });
});
