import { OpeningRange } from '../../restaurant/restaurant';

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

function minutes(time: string): number {
  const [hours, mins] = time.split(':').map(Number);
  return hours * 60 + mins;
}

function endsAfterMidnight(range: OpeningRange): boolean {
  return minutes(range.close) <= minutes(range.open);
}

/**
 * Closing time ("HH:mm") of the opening range that contains the targeted moment, or null when the
 * restaurant is closed then. The local date and time are read as written in the ISO string (time of
 * the search place); the offset is ignored. `weeklyHours` is indexed Sunday = 0.
 */
export function closingTimeAt(
  weeklyHours: readonly (readonly OpeningRange[])[],
  targetDateTime: string,
): string | null {
  const match = LOCAL_DATE_TIME.exec(targetDateTime);
  if (!match) {
    return null;
  }
  const [, year, month, day, hour, minute] = match.map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  const target = hour * 60 + minute;

  for (const range of weeklyHours[weekday] ?? []) {
    const open = minutes(range.open);
    const close = minutes(range.close);
    const contains = endsAfterMidnight(range) ? target >= open : target >= open && target < close;
    if (contains) {
      return range.close;
    }
  }

  const previousDay = (weekday + 6) % 7;
  for (const range of weeklyHours[previousDay] ?? []) {
    if (endsAfterMidnight(range) && target < minutes(range.close)) {
      return range.close;
    }
  }
  return null;
}
