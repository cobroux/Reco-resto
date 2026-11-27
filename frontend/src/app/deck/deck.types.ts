import { Restaurant } from '../restaurant/restaurant';
import { Position } from '../search/search-result';

export type ReactionKind = 'liked' | 'disliked' | 'picked';

/** Active reaction of the user to one restaurant of the deck. */
export interface Reaction {
  readonly restaurantId: string;
  readonly kind: ReactionKind;
}

/** What the search (feature 002) hands to the deck. */
export interface DeckInput {
  /** Raw moment expression ("ce soir", "samedi midi"); null when the sentence has none. */
  readonly momentLabel: string | null;
  /** Targeted local date-time of the search place, ISO 8601 ("2026-11-27T20:00:00+01:00"). */
  readonly targetDateTime: string;
  /** Device position when the user granted it; the results map shows it. Absent or null: unknown. */
  readonly userPosition?: Position | null;
  /** Ranked, most relevant first; already open at the targeted moment and with a known address. */
  readonly restaurants: readonly Restaurant[];
}
