import { cookingTossDuration } from './cooking-toss.token';

describe('cookingTossDuration (D21)', () => {
  const media = (reduce: boolean) => ({
    matchMedia: () => ({ matches: reduce }) as MediaQueryList,
  });

  it('lasts one second', () => {
    expect(cookingTossDuration(media(false))).toBe(1000);
  });

  it('adds no wait when the user prefers reduced motion', () => {
    expect(cookingTossDuration(media(true))).toBe(0);
  });

  it('lasts one second without matchMedia', () => {
    expect(cookingTossDuration(undefined)).toBe(1000);
  });
});
