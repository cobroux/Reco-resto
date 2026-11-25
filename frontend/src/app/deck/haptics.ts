import { ReactionKind } from './deck.types';

/** Vibration of each reaction, in ms (decision D19). */
export const HAPTIC_PATTERNS: Record<ReactionKind, number | number[]> = {
  disliked: 15,
  liked: 15,
  picked: [15, 60, 15],
};

/**
 * Vibrates for a validated reaction with the Web Vibration API (Android). iPhone has no Vibration
 * API: its tick comes from the native switches laid over the reaction buttons (deck component, D19).
 */
export function vibrateFor(
  kind: ReactionKind,
  target: Partial<Pick<Navigator, 'vibrate'>> | undefined = globalThis.navigator,
): void {
  if (typeof target?.vibrate === 'function') {
    target.vibrate(HAPTIC_PATTERNS[kind]);
  }
}
