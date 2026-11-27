import { DataProvider, PhotoAttribution, Position } from '../search/search-result';

/** Opening range of one day, local time of the restaurant, "HH:mm". A close before open ends after midnight. */
export interface OpeningRange {
  readonly open: string;
  readonly close: string;
}

/** Dish one can eat at the restaurant ("À goûter"). */
export interface Dish {
  readonly name: string;
  readonly photoUrl?: string;
}

/** Dish of the menu (shape of feature 002's `MenuItem`); description and price are shown as given. */
export interface MenuItem {
  readonly name: string;
  readonly description?: string;
  readonly price?: string;
}

/**
 * Restaurant as shown by the swipe deck (subset of feature 002's Restaurant).
 * See specs/003-swipe-deck/data-model.md.
 */
export interface Restaurant {
  readonly id: string;
  readonly name: string;
  readonly distanceMeters: number;
  readonly cuisine: string;
  readonly keywords: readonly string[];
  readonly priceLevel?: string;
  /** Seven entries indexed like `Date.getUTCDay()`: 0 = Sunday … 6 = Saturday. Empty = closed. */
  readonly weeklyHours: readonly (readonly OpeningRange[])[];
  readonly photoUrl?: string;
  readonly dish?: Dish;
  /** Postal address when known, shown under the name (added at the request of the team; not in spec 003). */
  readonly address?: string;
  /** Coordinates when the data source gives them; the results map needs them. */
  readonly location?: Position;
  /** Website of the restaurant (spec 008). */
  readonly website?: string;
  /** Authors of the photo, shown with it (spec 008, FR-014). */
  readonly photoAttributions?: readonly PhotoAttribution[];
  /** The photo on Google Maps (spec 008). */
  readonly photoSourceUri?: string;
  /** Data providers shown with "Google Maps" (spec 008, FR-014). */
  readonly attributions?: readonly DataProvider[];
  /** Menu shown on the back of the card (spec 004). Absent or empty: no "Menu" section. */
  readonly menu?: readonly MenuItem[];
  /** Carried for a future "Appeler" action; not displayed (plan D-04-8). */
  readonly phone?: string;
  /** Why the model picked this restaurant, shown on the card. */
  readonly reason?: string;
}
