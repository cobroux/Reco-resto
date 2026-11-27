import { Restaurant } from './restaurant';

/**
 * Google Maps route from the user's position to the restaurant (Maps URLs, no API key). The name keeps the
 * destination on the place rather than on the bare address.
 */
export function googleMapsRouteUrl(restaurant: Pick<Restaurant, 'name' | 'address'>): string {
  const destination = [restaurant.name, restaurant.address].filter(Boolean).join(', ');
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destination)}`;
}
