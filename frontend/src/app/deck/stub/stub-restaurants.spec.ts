import { closingTimeAt } from '../format/opening';
import { STUB_DECK_INPUT } from './stub-restaurants';

describe('STUB_DECK_INPUT (D6)', () => {
  const restaurants = STUB_DECK_INPUT.restaurants;

  it('has 16 restaurants so the deck size cap of 15 is visible', () => {
    expect(restaurants.length).toBe(16);
  });

  it('has unique ids', () => {
    expect(new Set(restaurants.map((r) => r.id)).size).toBe(restaurants.length);
  });

  it('mixes at least three cuisines', () => {
    expect(new Set(restaurants.map((r) => r.cuisine)).size).toBeGreaterThanOrEqual(3);
  });

  it('has at least one restaurant without photo and one without dish', () => {
    expect(restaurants.some((r) => !r.photoUrl)).toBe(true);
    expect(restaurants.some((r) => !r.dish)).toBe(true);
  });

  it('only has restaurants open at the targeted moment', () => {
    for (const r of restaurants) {
      expect(closingTimeAt(r.weeklyHours, STUB_DECK_INPUT.targetDateTime), r.name).not.toBeNull();
    }
  });

  it('targets "ce soir"', () => {
    expect(STUB_DECK_INPUT.momentLabel).toBe('ce soir');
  });
});
