import { OpeningRange } from '../../restaurant/restaurant';
import { DECK_TEXTS } from '../deck.texts';
import { targetWeekday } from './opening';

export interface WeeklyHoursRow {
  readonly day: string;
  readonly ranges: string;
  readonly targeted: boolean;
}

/** French day names indexed like `weeklyHours`: Sunday = 0. */
const DAY_NAMES = ['Dimanche', 'Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi'];

/** Order of the rows on the back of the card: Monday first. */
const DISPLAY_ORDER = [1, 2, 3, 4, 5, 6, 0];

/**
 * Rows of the "Horaires" section (spec 004, FR-002, FR-004): one per day, Monday first, the targeted
 * day flagged. Empty when no day has a range, so the section is not displayed (FR-003).
 */
export function weeklyHoursRows(
  weeklyHours: readonly (readonly OpeningRange[])[],
  targetDateTime: string,
): WeeklyHoursRow[] {
  if (!weeklyHours.some((ranges) => ranges.length > 0)) {
    return [];
  }
  const targeted = targetWeekday(targetDateTime);
  return DISPLAY_ORDER.map((weekday) => {
    const ranges = weeklyHours[weekday] ?? [];
    return {
      day: DAY_NAMES[weekday],
      ranges: ranges.length
        ? ranges.map((range) => `${range.open} – ${range.close}`).join(' · ')
        : DECK_TEXTS.closed,
      targeted: weekday === targeted,
    };
  });
}
