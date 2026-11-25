import { InjectionToken } from '@angular/core';
import { prefersReducedMotion } from './motion';

/** One crêpe toss of the "En cuisine…" scene (D21): 1 s, no wait when reduced motion is preferred. */
export function cookingTossDuration(win: Pick<Window, 'matchMedia'> | undefined): number {
  return prefersReducedMotion(win) ? 0 : 1000;
}

/** Minimum wait between a reaction and the next card (FR-018), and duration of the toss animation. */
export const COOKING_TOSS_MS = new InjectionToken<number>('COOKING_TOSS_MS', {
  factory: () => cookingTossDuration(globalThis.window),
});
