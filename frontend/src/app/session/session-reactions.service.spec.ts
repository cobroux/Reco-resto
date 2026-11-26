import { TestBed } from '@angular/core/testing';
import { STUB_DECK_INPUT } from '../deck/stub/stub-restaurants';
import { SessionReactionsService } from './session-reactions.service';

describe('SessionReactionsService (FR-011)', () => {
  let service: SessionReactionsService;
  const restaurants = STUB_DECK_INPUT.restaurants;

  beforeEach(() => {
    service = TestBed.inject(SessionReactionsService);
  });

  it('starts empty', () => {
    expect(service.restaurants()).toEqual([]);
    expect(service.reactions()).toEqual([]);
  });

  it('exposes the deck restaurants and the active reactions', () => {
    const reactions = [{ restaurantId: restaurants[0].id, kind: 'liked' as const }];
    service.set(restaurants, reactions);

    expect(service.restaurants()).toBe(restaurants);
    expect(service.reactions()).toBe(reactions);
  });

  it('finds a restaurant by id', () => {
    service.set(restaurants, []);

    expect(service.restaurant(restaurants[3].id)).toBe(restaurants[3]);
    expect(service.restaurant('unknown')).toBeUndefined();
  });
  describe('setReaction (results screen)', () => {
    const [a, b, c] = restaurants;

    beforeEach(() => {
      service.set(restaurants, [
        { restaurantId: a.id, kind: 'liked' },
        { restaurantId: b.id, kind: 'picked' },
        { restaurantId: c.id, kind: 'disliked' },
      ]);
    });

    it('replaces the reaction of a restaurant and keeps the order of the others', () => {
      service.setReaction(a.id, 'disliked');

      expect(service.reactions()).toEqual([
        { restaurantId: a.id, kind: 'disliked' },
        { restaurantId: b.id, kind: 'picked' },
        { restaurantId: c.id, kind: 'disliked' },
      ]);
    });

    it('keeps a single picked restaurant: the previous one goes back to liked', () => {
      service.setReaction(a.id, 'picked');

      expect(service.reactions()).toEqual([
        { restaurantId: a.id, kind: 'picked' },
        { restaurantId: b.id, kind: 'liked' },
        { restaurantId: c.id, kind: 'disliked' },
      ]);
    });

    it('does nothing for a restaurant without reaction', () => {
      const before = service.reactions();
      service.setReaction(restaurants[3].id, 'liked');

      expect(service.reactions()).toBe(before);
    });
  });
});
