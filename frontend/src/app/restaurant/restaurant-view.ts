import { formatDistance } from '../deck/format/distance';
import { closingTimeAt } from '../deck/format/opening';
import { weeklyHoursRows } from '../deck/format/weekly-hours';
import { Restaurant } from './restaurant';

/** A restaurant with the values derived for display (tile, detail), shared by the results and choice screens. */
export interface RestaurantView {
  readonly id: string;
  readonly restaurant: Restaurant;
  readonly distance: string;
  readonly closing: string | null;
  readonly tag: string | undefined;
  readonly hours: ReturnType<typeof weeklyHoursRows>;
}

export function toRestaurantView(restaurant: Restaurant, targetDateTime: string): RestaurantView {
  return {
    id: restaurant.id,
    restaurant,
    distance: formatDistance(restaurant.distanceMeters),
    closing: closingTimeAt(restaurant.weeklyHours, targetDateTime),
    tag: restaurant.keywords[0],
    hours: weeklyHoursRows(restaurant.weeklyHours, targetDateTime),
  };
}
