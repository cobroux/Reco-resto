import { STUB_RESTAURANTS } from '../deck/stub/stub-restaurants';
import { toRestaurantView } from './restaurant-view';

describe('toRestaurantView', () => {
  it('derives the display values of a restaurant', () => {
    const restaurant = STUB_RESTAURANTS[0];
    const view = toRestaurantView(restaurant, '');
    expect(view.id).toBe(restaurant.id);
    expect(view.restaurant).toBe(restaurant);
    expect(view.tag).toBe(restaurant.keywords[0]);
    expect(view.distance).not.toBe('');
    expect(view.hours.length).toBe(restaurant.weeklyHours.length ? 7 : 0);
  });
});
