import { classifySwipe, isDragging } from './swipe-gesture';

describe('swipe gesture (D5)', () => {
  it('starts a drag only beyond 6 px', () => {
    expect(isDragging(6, 0)).toBe(false);
    expect(isDragging(0, -6)).toBe(false);
    expect(isDragging(7, 0)).toBe(true);
    expect(isDragging(0, -7)).toBe(true);
  });

  it.each([
    [110, 0, 'right'],
    [200, -60, 'right'],
    [-110, 0, 'left'],
    [-150, 40, 'left'],
    [0, -110, 'up'],
    [60, -150, 'up'],
  ] as const)('dx %i, dy %i is a %s swipe', (dx, dy, expected) => {
    expect(classifySwipe(dx, dy)).toBe(expected);
  });

  it.each([
    [109, 0],
    [-109, 0],
    [0, -109],
    [0, 200],
    [0, 0],
  ])('dx %i, dy %i is under the thresholds', (dx, dy) => {
    expect(classifySwipe(dx, dy)).toBe('none');
  });

  it('prefers the horizontal direction when the movement is mostly horizontal', () => {
    expect(classifySwipe(130, -120)).toBe('right');
  });

  it('prefers up when the vertical movement dominates', () => {
    expect(classifySwipe(120, -130)).toBe('up');
  });
});
