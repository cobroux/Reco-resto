import { OpeningRange } from '../../restaurant/restaurant';
import { closingTimeAt } from './opening';

const lunchAndDinner: OpeningRange[] = [
  { open: '12:00', close: '14:30' },
  { open: '19:00', close: '23:00' },
];

/** Seven days indexed Sunday = 0; `days` maps a weekday index to its ranges. */
function week(days: Record<number, OpeningRange[]>): OpeningRange[][] {
  return [0, 1, 2, 3, 4, 5, 6].map((d) => days[d] ?? []);
}

describe('closingTimeAt (D8)', () => {
  // 2026-11-27 is a Friday (5), 2026-11-28 a Saturday (6).
  it('returns the end of the lunch range when the target is at lunch', () => {
    expect(closingTimeAt(week({ 5: lunchAndDinner }), '2026-11-27T13:00:00+01:00')).toBe('14:30');
  });

  it('returns the end of the dinner range when the target is in the evening', () => {
    expect(closingTimeAt(week({ 5: lunchAndDinner }), '2026-11-27T20:00:00+01:00')).toBe('23:00');
  });

  it('includes the opening minute and excludes the closing minute', () => {
    const hours = week({ 5: lunchAndDinner });
    expect(closingTimeAt(hours, '2026-11-27T19:00')).toBe('23:00');
    expect(closingTimeAt(hours, '2026-11-27T23:00')).toBeNull();
  });

  it('returns null between two ranges', () => {
    expect(closingTimeAt(week({ 5: lunchAndDinner }), '2026-11-27T16:00:00+01:00')).toBeNull();
  });

  it('uses the weekday of the target, not today', () => {
    const hours = week({ 5: [], 6: lunchAndDinner });
    expect(closingTimeAt(hours, '2026-11-28T12:30:00+01:00')).toBe('14:30');
    expect(closingTimeAt(hours, '2026-11-27T12:30:00+01:00')).toBeNull();
  });

  it('handles a range ending after midnight, on the same evening and the next night', () => {
    const hours = week({ 5: [{ open: '19:00', close: '01:00' }] });
    expect(closingTimeAt(hours, '2026-11-27T23:30:00+01:00')).toBe('01:00');
    expect(closingTimeAt(hours, '2026-11-28T00:30:00+01:00')).toBe('01:00');
    expect(closingTimeAt(hours, '2026-11-28T01:30:00+01:00')).toBeNull();
  });

  it('reads the local date and time as written, whatever the offset', () => {
    expect(closingTimeAt(week({ 5: lunchAndDinner }), '2026-11-27T22:30:00-08:00')).toBe('23:00');
  });
});
