import { Injectable, Signal, signal } from '@angular/core';
import { Reaction, ReactionKind } from '../deck/deck.types';
import { Restaurant } from '../restaurant/restaurant';

/**
 * Reactions of the current browser session (FR-011), read by features 005 and 006.
 * In memory only: lost on page reload. Written by the deck store, and by the results screen through
 * {@link setReaction}.
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

  /**
   * Changes the reaction of a restaurant that already has one (results screen). Only one restaurant can
   * be picked: choosing another one sends the previous pick back to liked. A restaurant without
   * reaction is left alone.
   */
  setReaction(restaurantId: string, kind: ReactionKind): void {
    const current = this.reactionsState();
    if (!current.some((reaction) => reaction.restaurantId === restaurantId)) {
      return;
    }
    this.reactionsState.set(
      current.map((reaction) => {
        if (reaction.restaurantId === restaurantId) {
          return { restaurantId, kind };
        }
        return kind === 'picked' && reaction.kind === 'picked'
          ? { restaurantId: reaction.restaurantId, kind: 'liked' }
          : reaction;
      }),
    );
  }

  restaurant(id: string): Restaurant | undefined {
    return this.restaurantsState().find((r) => r.id === id);
  }
}
