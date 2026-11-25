import { vi } from 'vitest';
import { HAPTIC_PATTERNS, vibrateFor } from './haptics';

describe('haptics (D19)', () => {
  afterEach(() => vi.restoreAllMocks());

  it('uses a short pulse for like and dislike, two pulses for a pick', () => {
    expect(HAPTIC_PATTERNS.liked).toBe(15);
    expect(HAPTIC_PATTERNS.disliked).toBe(15);
    expect(HAPTIC_PATTERNS.picked).toEqual([15, 60, 15]);
  });

  it('vibrates with the pattern of the reaction (Android)', () => {
    const vibrate = vi.fn(() => true);

    vibrateFor('picked', { vibrate });

    expect(vibrate).toHaveBeenCalledWith([15, 60, 15]);
  });

  it('does nothing where vibration is not supported, without toggling any switch (iPhone)', () => {
    const click = vi.spyOn(HTMLElement.prototype, 'click');

    expect(() => vibrateFor('liked', {})).not.toThrow();
    expect(() => vibrateFor('liked', undefined)).not.toThrow();
    expect(click).not.toHaveBeenCalled();
  });
});
