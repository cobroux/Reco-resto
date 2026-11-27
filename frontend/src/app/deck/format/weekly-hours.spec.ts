import { OpeningRange } from '../../restaurant/restaurant';
import { weeklyHoursRows } from './weekly-hours';

const lunchAndDinner: OpeningRange[] = [
  { open: '12:00', close: '14:30' },
  { open: '19:00', close: '22:30' },
];

/** Seven days indexed Sunday = 0. */
function week(days: Record<number, OpeningRange[]>): OpeningRange[][] {
  return [0, 1, 2, 3, 4, 5, 6].map((d) => days[d] ?? []);
}

describe('weeklyHoursRows (spec 004, FR-002, FR-004)', () => {
  // 2026-11-27 is a Friday.
  const FRIDAY = '2026-11-27T20:00:00+01:00';

  it('lists the seven days, Monday first, with French names', () => {
    const rows = weeklyHoursRows(week({ 1: lunchAndDinner }), FRIDAY);

    expect(rows.map((row) => row.day)).toEqual([
      'Lundi',
      'Mardi',
      'Mercredi',
      'Jeudi',
      'Vendredi',
      'Samedi',
      'Dimanche',
    ]);
  });

  it('writes the ranges of a day on one line', () => {
    const rows = weeklyHoursRows(week({ 1: lunchAndDinner }), FRIDAY);

    expect(rows[0].ranges).toBe('12:00 – 14:30 · 19:00 – 22:30');
  });

  it('writes "Fermé" for a day without range', () => {
    const rows = weeklyHoursRows(week({ 1: lunchAndDinner }), FRIDAY);

    expect(rows[1].ranges).toBe('Fermé');
  });

  it('flags only the targeted day', () => {
    const rows = weeklyHoursRows(week({ 1: lunchAndDinner }), FRIDAY);

    expect(rows.filter((row) => row.targeted).map((row) => row.day)).toEqual(['Vendredi']);
  });

  it('flags the day of the moment of the sentence, not today', () => {
    const rows = weeklyHoursRows(week({ 1: lunchAndDinner }), '2026-11-29T12:00:00+01:00');

    expect(rows.filter((row) => row.targeted).map((row) => row.day)).toEqual(['Dimanche']);
  });

  it('flags no day when the target is invalid', () => {
    const rows = weeklyHoursRows(week({ 1: lunchAndDinner }), 'tonight');

    expect(rows.some((row) => row.targeted)).toBe(false);
  });

  it('returns no row when no day has a range (the section is not displayed)', () => {
    expect(weeklyHoursRows(week({}), FRIDAY)).toEqual([]);
  });
});
