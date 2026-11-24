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
}
