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
});
