import { Injectable, Signal, signal } from '@angular/core';
import { Reaction } from '../deck/deck.types';
import { Restaurant } from '../restaurant/restaurant';

/**
 * Reactions of the current browser session (FR-011), read by features 005 and 006.
 * In memory only: lost on page reload. Written by the deck store only.
 */
@Injectable({ providedIn: 'root' })
export class SessionReactionsService {
  private readonly restaurantsState = signal<readonly Restaurant[]>([]);
  private readonly reactionsState = signal<readonly Reaction[]>([]);

  readonly restaurants: Signal<readonly Restaurant[]> = this.restaurantsState.asReadonly();
  /** Active reactions only (reactions cancelled by rewind are removed). */
  readonly reactions: Signal<readonly Reaction[]> = this.reactionsState.asReadonly();

  set(restaurants: readonly Restaurant[], reactions: readonly Reaction[]): void {
    this.restaurantsState.set(restaurants);
    this.reactionsState.set(reactions);
  }

  restaurant(id: string): Restaurant | undefined {
    return this.restaurantsState().find((r) => r.id === id);
  }
}
