import { DeckInput } from '../deck/deck.types';
import { OpeningRange, Restaurant as DeckRestaurant } from '../restaurant/restaurant';
import { Restaurant, SearchResult } from './search-result';

const PARIS = 'Europe/Paris';
const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

/** Days of the backend (upper-case English names) in the order of the deck: index 0 = Sunday. */
const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

/** Offset of Paris at the given instant, "+01:00" or "+02:00". */
function parisOffset(instant: Date): string {
  const zone = new Intl.DateTimeFormat('en-US', { timeZone: PARIS, timeZoneName: 'longOffset' })
    .formatToParts(instant)
    .find((part) => part.type === 'timeZoneName')?.value;
  return (zone ?? 'GMT').replace('GMT', '');
}

/** Paris local date-time of an instant, "2026-11-25T11:00:00+01:00". */
function parisNow(now: Date): string {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: PARIS,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(now)
      .map((part) => [part.type, part.value]),
  );
  return `${parts['year']}-${parts['month']}-${parts['day']}T${parts['hour']}:${parts['minute']}:${parts['second']}${parisOffset(now)}`;
}

/**
 * Targeted moment as the deck wants it: the local date-time of Paris. "now" or no moment is the current time; an
 * explicit moment is kept as written and gets the Paris offset of that day (the deck reads the date and time as
 * written and ignores the offset).
 */
function targetDateTime(moment: string | undefined, now: Date): string {
  const match = moment ? LOCAL_DATE_TIME.exec(moment) : null;
  if (!match) {
    return parisNow(now);
  }
  const [, year, month, day, hour, minute] = match;
  const asInstant = new Date(Date.UTC(+year, +month - 1, +day, +hour, +minute));
  return `${year}-${month}-${day}T${hour}:${minute}:00${parisOffset(asInstant)}`;
}

function weeklyHours(restaurant: Restaurant): OpeningRange[][] {
  return DAYS.map((day) =>
    (restaurant.openingHours?.[day] ?? []).map((period) => ({
      open: period.opens,
      close: period.closes,
    })),
  );
}

function toDeckRestaurant(restaurant: Restaurant & { address: string; distanceMeters: number }): DeckRestaurant {
  return {
    // Spec 008 (research R11): the Google place id identifies a place; older sources fall back to name|address.
    id: restaurant.placeId ?? `${restaurant.name}|${restaurant.address}`,
    name: restaurant.name,
    address: restaurant.address,
    distanceMeters: restaurant.distanceMeters,
    cuisine: restaurant.cuisine ?? '',
    keywords: [...(restaurant.keywords ?? []), ...(restaurant.diets ?? [])],
    priceLevel: restaurant.priceLevel,
    weeklyHours: weeklyHours(restaurant),
    photoUrl: restaurant.photoUrl,
    dish: restaurant.signatureDish ? { name: restaurant.signatureDish } : undefined,
    website: restaurant.website,
    photoAttributions: restaurant.photoAttributions,
    photoSourceUri: restaurant.photoSourceUri,
    attributions: restaurant.attributions,
    menu: restaurant.menu,
    phone: restaurant.phone,
    reason: restaurant.reason,
  };
}

/**
 * Hands the result of the search (spec 002) to the swipe deck (spec 003, FR-019 to FR-021).
 * The backend already ranked the deck and kept only restaurants open at the targeted moment. The moment label
 * ("ce soir") is not returned by the backend, so the deck title uses its default. The cuisines and diets are
 * shown as the backend gives them (OpenStreetMap values, not translated).
 */
export function toDeckInput(result: SearchResult, now: Date = new Date()): DeckInput {
  return {
    momentLabel: null,
    targetDateTime: targetDateTime(result.criteria.moment, now),
    restaurants: result.deck
      .filter(
        (restaurant): restaurant is Restaurant & { address: string; distanceMeters: number } =>
          !!restaurant.address && restaurant.distanceMeters !== undefined,
      )
      .map(toDeckRestaurant),
  };
}
