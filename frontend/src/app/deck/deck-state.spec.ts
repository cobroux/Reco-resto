import { Restaurant } from '../restaurant/restaurant';
import {
  DeckState,
  activeReactions,
  advance,
  applyRefinement,
  canRewind,
  confirmPick,
  createDeck,
  currentCard,
  markRefinementFailed,
  position,
  react,
  recordReaction,
  refinementRequest,
  returnFromPick,
  rewind,
} from './deck-state';

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

const ids = (list: readonly Restaurant[]) => list.map((r) => r.id);
const shownIds = (s: DeckState) => s.shown.map((c) => c.restaurant.id);

describe('deck-state', () => {
  describe('creation (US1, FR-002)', () => {
    it('takes the deck size when more restaurants match', () => {
      const s = createDeck(restaurants(16), 15);

      expect(s.size).toBe(15);
      expect(position(s)).toBe(1);
      expect(currentCard(s)?.id).toBe('r1');
      expect(ids(s.planned)).toEqual(ids(restaurants(15)).slice(1));
      expect(s.status).toBe('browsing');
      expect(s.refinementDegraded).toBe(false);
    });

    it('keeps all restaurants as candidates for refinement', () => {
      expect(createDeck(restaurants(16), 15).candidates.length).toBe(16);
    });

    it('takes only the restaurants found when fewer than the deck size (US5 AS2)', () => {
      const s = createDeck(restaurants(4), 15);

      expect(s.size).toBe(4);
      expect(s.shown.length + s.planned.length).toBe(4);
    });

    it('cannot rewind on the first card (US4 AS3)', () => {
      expect(canRewind(createDeck(restaurants(5), 15))).toBe(false);
    });

    it('is finished at once when there is no restaurant', () => {
      expect(createDeck([], 15).status).toBe('finished');
    });
  });

  describe('reactions (US2)', () => {
    it('records a dislike and shows the next card (AS1)', () => {
      const s = react(createDeck(restaurants(5), 15), 'disliked');

      expect(currentCard(s)?.id).toBe('r2');
      expect(position(s)).toBe(2);
      expect(activeReactions(s)).toEqual([{ restaurantId: 'r1', kind: 'disliked' }]);
    });

    it('records a like and shows the next card (AS2)', () => {
      const s = react(createDeck(restaurants(5), 15), 'liked');

      expect(currentCard(s)?.id).toBe('r2');
      expect(activeReactions(s)).toEqual([{ restaurantId: 'r1', kind: 'liked' }]);
    });

    it('records a pick without moving to the next card (AS3)', () => {
      const s = react(createDeck(restaurants(5), 15), 'picked');

      expect(s.status).toBe('picked');
      expect(currentCard(s)?.id).toBe('r1');
      expect(activeReactions(s)).toEqual([{ restaurantId: 'r1', kind: 'picked' }]);
    });

    it('turns a pick into a like and goes on at the next card on return (FR-014)', () => {
      const s = returnFromPick(react(createDeck(restaurants(5), 15), 'picked'));

      expect(s.status).toBe('browsing');
      expect(currentCard(s)?.id).toBe('r2');
      expect(activeReactions(s)).toEqual([{ restaurantId: 'r1', kind: 'liked' }]);
    });

    it('ignores reactions when not browsing', () => {
      const picked = react(createDeck(restaurants(5), 15), 'picked');

      expect(react(picked, 'liked')).toBe(picked);
    });

    it('keeps the deck size constant (rule R2)', () => {
      let s = createDeck(restaurants(16), 15);
      for (let i = 0; i < 5; i++) {
        s = react(s, 'liked');
        expect(s.shown.length + s.planned.length).toBe(15);
      }
    });
  });

  describe('refinement (US3)', () => {
    it('builds the request from active reactions, shown cards and slots left', () => {
      const s = recordReaction(react(createDeck(restaurants(6), 15), 'liked'), 'disliked');

      expect(refinementRequest(s)).toEqual({
        candidates: s.candidates,
        reactions: [
          { restaurantId: 'r1', kind: 'liked' },
          { restaurantId: 'r2', kind: 'disliked' },
        ],
        excludedIds: ['r1', 'r2'],
        slots: 4,
      });
    });

    it('replaces the planned cards with the result (AS1)', () => {
      const s = recordReaction(createDeck(restaurants(8), 4), 'liked');
      const all = restaurants(8);

      const refined = applyRefinement(s, [all[6], all[5], all[7]]);

      expect(ids(refined.planned)).toEqual(['r7', 'r6', 'r8']);
    });

    it('never plans a restaurant already shown, whatever the result (R1, SC-003)', () => {
      const s = recordReaction(react(createDeck(restaurants(8), 4), 'liked'), 'liked');
      const all = restaurants(8);

      const refined = applyRefinement(s, [all[0], all[1], all[6], all[6], all[7]]);

      expect(ids(refined.planned)).toEqual(['r7', 'r8']);
    });

    it('truncates a long result and completes a short one with the previous plan (R2, R3, AS2)', () => {
      const all = restaurants(10);
      const s = recordReaction(createDeck(all, 5), 'liked');

      expect(applyRefinement(s, all.slice(5)).planned.length).toBe(4);
      expect(ids(applyRefinement(s, [all[9]]).planned)).toEqual(['r10', 'r2', 'r3', 'r4']);
    });

    it('flags a failed refinement and keeps the plan, then clears the flag on success (FR-013)', () => {
      const s = recordReaction(createDeck(restaurants(6), 15), 'liked');

      const failed = markRefinementFailed(s);
      expect(failed.refinementDegraded).toBe(true);
      expect(failed.planned).toBe(s.planned);

      expect(applyRefinement(failed, []).refinementDegraded).toBe(false);
    });

    it('moves to the next planned card with advance', () => {
      const s = advance(recordReaction(createDeck(restaurants(3), 15), 'liked'));

      expect(currentCard(s)?.id).toBe('r2');
    });
  });

  describe('rewind (US4)', () => {
    it('shows the previous card again and cancels its reaction (AS1)', () => {
      const s = rewind(react(react(createDeck(restaurants(6), 15), 'liked'), 'disliked'));

      expect(currentCard(s)?.id).toBe('r2');
      expect(activeReactions(s)).toEqual([{ restaurantId: 'r1', kind: 'liked' }]);
      expect(s.shown.length + s.planned.length).toBe(6);
    });

    it('goes back as far as the first card, then does nothing (AS2, AS3)', () => {
      let s = createDeck(restaurants(6), 15);
      s = react(react(react(s, 'liked'), 'liked'), 'disliked');

      s = rewind(rewind(rewind(s)));
      expect(currentCard(s)?.id).toBe('r1');
      expect(activeReactions(s)).toEqual([]);
      expect(canRewind(s)).toBe(false);
      expect(rewind(s)).toBe(s);
    });

    it('makes rewound-past cards eligible again (D7)', () => {
      let s = createDeck(restaurants(6), 15);
      s = react(react(react(react(s, 'liked'), 'liked'), 'liked'), 'liked'); // current r5
      s = rewind(rewind(s)); // current r3, r4 and r5 no longer shown

      expect(refinementRequest(s).excludedIds).toEqual(['r1', 'r2', 'r3']);
      expect(ids(applyRefinement(s, restaurants(6).slice(3)).planned)).toEqual(['r4', 'r5', 'r6']);
    });
  });

  describe('end of deck (US5)', () => {
    it('finishes on a like or dislike of the last card (AS1)', () => {
      let s = createDeck(restaurants(2), 15);
      s = react(react(s, 'liked'), 'disliked');

      expect(s.status).toBe('finished');
      expect(activeReactions(s)).toEqual([
        { restaurantId: 'r1', kind: 'liked' },
        { restaurantId: 'r2', kind: 'disliked' },
      ]);
    });

    it('finishes when returning from a pick on the last card (D12)', () => {
      let s = react(createDeck(restaurants(2), 15), 'liked');
      s = returnFromPick(react(s, 'picked'));

      expect(s.status).toBe('finished');
      expect(activeReactions(s)).toContainEqual({ restaurantId: 'r2', kind: 'liked' });
    });

    it('cannot rewind once finished', () => {
      const s = react(createDeck(restaurants(1), 15), 'disliked');

      expect(canRewind(s)).toBe(false);
    });

    it('confirmPick only applies to a picked deck', () => {
      const s = createDeck(restaurants(2), 15);

      expect(confirmPick(s)).toBe(s);
    });
  });
});
