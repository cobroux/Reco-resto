import { formatDistance } from './distance';

describe('formatDistance (D15)', () => {
  it.each([
    [349, '350 m'],
    [600, '600 m'],
    [44, '40 m'],
    [994, '990 m'],
  ])('shows %i m in metres rounded to 10 m: %s', (meters, expected) => {
    expect(formatDistance(meters)).toBe(expected);
  });

  it.each([
    [995, '1,0 km'],
    [999, '1,0 km'],
    [1000, '1,0 km'],
    [1149, '1,1 km'],
    [1560, '1,6 km'],
    [12345, '12,3 km'],
  ])('shows %i m in kilometres with one decimal and a comma: %s', (meters, expected) => {
    expect(formatDistance(meters)).toBe(expected);
  });
});
