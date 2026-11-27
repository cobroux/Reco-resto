import { InjectionToken } from '@angular/core';

/**
 * Maximum number of cards of a deck (FR-002). Temporary frontend source of the value until
 * feature 002 owns it; the token is then removed (decision D17).
 */
export const DECK_SIZE = new InjectionToken<number>('DECK_SIZE', { factory: () => 15 });
