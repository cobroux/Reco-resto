import { Restaurant } from '../restaurant/restaurant';
import { Reaction, ReactionKind } from './deck.types';
import { RefinementRequest } from './refinement.service';

/**
 * Pure state machine of the swipe deck. See specs/003-swipe-deck/data-model.md (DeckState, rules
 * R1–R3, state transitions). Every function returns a new state, or the same one when nothing applies.
 */

export type DeckStatus = 'browsing' | 'picked' | 'finished';

export interface ShownCard {
  readonly restaurant: Restaurant;
  readonly reaction?: ReactionKind;
}

export interface DeckState {
  /** Fixed number of cards of the deck. */
  readonly size: number;
  /** Cards already displayed, in order; while browsing, the last one is the current card. */
  readonly shown: readonly ShownCard[];
  /** Cards to come; `shown.length + planned.length === size` (R2). */
  readonly planned: readonly Restaurant[];
  /** All restaurants of the deck input: the pool for refinement. */
  readonly candidates: readonly Restaurant[];
  readonly refinementDegraded: boolean;
  readonly status: DeckStatus;
}

export function createDeck(restaurants: readonly Restaurant[], deckSize: number): DeckState {
  const deck = restaurants.slice(0, deckSize);
  return {
    size: deck.length,
    shown: deck.length ? [{ restaurant: deck[0] }] : [],
    planned: deck.slice(1),
    candidates: restaurants,
    refinementDegraded: false,
    status: deck.length ? 'browsing' : 'finished',
  };
}

export function currentCard(state: DeckState): Restaurant | undefined {
  return state.shown.at(-1)?.restaurant;
}

/** 1-based position of the current card. */
export function position(state: DeckState): number {
  return state.shown.length;
}

export function canRewind(state: DeckState): boolean {
  return state.status === 'browsing' && state.shown.length > 1;
}

export function activeReactions(state: DeckState): Reaction[] {
  return state.shown
    .filter((card) => card.reaction)
    .map((card) => ({ restaurantId: card.restaurant.id, kind: card.reaction! }));
}

function withCurrentReaction(state: DeckState, reaction: ReactionKind | undefined): ShownCard[] {
  const shown = [...state.shown];
  shown[shown.length - 1] = { restaurant: shown[shown.length - 1].restaurant, reaction };
  return shown;
}

/** Records the reaction to the current card without moving on; a pick puts the deck on hold. */
export function recordReaction(state: DeckState, kind: ReactionKind): DeckState {
  if (state.status !== 'browsing' || state.shown.length === 0) {
    return state;
  }
  return {
    ...state,
    shown: withCurrentReaction(state, kind),
    status: kind === 'picked' ? 'picked' : 'browsing',
  };
}

/** Shows the next planned card, or finishes the deck when none is left. */
export function advance(state: DeckState): DeckState {
  if (state.status !== 'browsing') {
    return state;
  }
  if (state.planned.length === 0) {
    return { ...state, status: 'finished' };
  }
  return {
    ...state,
    shown: [...state.shown, { restaurant: state.planned[0] }],
    planned: state.planned.slice(1),
  };
}

/** Records a reaction and, unless it is a pick, moves to the next card (no refinement). */
export function react(state: DeckState, kind: ReactionKind): DeckState {
  const reacted = recordReaction(state, kind);
  return reacted === state || kind === 'picked' ? reacted : advance(reacted);
}

/** Back from the route screen: the picked restaurant counts as liked (FR-014). */
export function confirmPick(state: DeckState): DeckState {
  if (state.status !== 'picked') {
    return state;
  }
  return { ...state, shown: withCurrentReaction(state, 'liked'), status: 'browsing' };
}

export function returnFromPick(state: DeckState): DeckState {
  const confirmed = confirmPick(state);
  return confirmed === state ? state : advance(confirmed);
}

/** Shows the previous card again and cancels its reaction (FR-007); the current card is planned again. */
export function rewind(state: DeckState): DeckState {
  if (!canRewind(state)) {
    return state;
  }
  const current = state.shown[state.shown.length - 1];
  const previous = { ...state, shown: state.shown.slice(0, -1) };
  return {
    ...previous,
    shown: withCurrentReaction(previous, undefined),
    planned: [current.restaurant, ...state.planned],
  };
}

export function refinementRequest(state: DeckState): RefinementRequest {
  return {
    candidates: state.candidates,
    reactions: activeReactions(state),
    excludedIds: state.shown.map((card) => card.restaurant.id),
    slots: state.planned.length,
  };
}

/**
 * Replaces the planned cards with a refinement result, enforcing R1 (no shown restaurant, no
 * duplicate) and R2/R3 (truncated to the slots left, completed with the previous plan).
 */
export function applyRefinement(state: DeckState, result: readonly Restaurant[]): DeckState {
  const slots = state.planned.length;
  const taken = new Set(state.shown.map((card) => card.restaurant.id));
  const planned: Restaurant[] = [];
  for (const restaurant of [...result, ...state.planned]) {
    if (planned.length === slots) {
      break;
    }
    if (!taken.has(restaurant.id)) {
      taken.add(restaurant.id);
      planned.push(restaurant);
    }
  }
  return { ...state, planned, refinementDegraded: false };
}

/** Refinement failed: the plan is kept and the user is told (FR-013). */
export function markRefinementFailed(state: DeckState): DeckState {
  return { ...state, refinementDegraded: true };
}

/**
 * Replaces restaurants of the deck by an updated version of themselves (same position in `from` and `to`, matched by
 * identity, not by id: two restaurants can share an id), keeping the reactions, the order and the status. Used when
 * the walking distances arrive after the deck opened.
 */
export function swapRestaurants(
  state: DeckState,
  from: readonly Restaurant[],
  to: readonly Restaurant[],
): DeckState {
  const replacement = new Map(from.map((restaurant, i) => [restaurant, to[i] ?? restaurant]));
  const swap = (restaurant: Restaurant) => replacement.get(restaurant) ?? restaurant;
  return {
    ...state,
    shown: state.shown.map((card) => ({ ...card, restaurant: swap(card.restaurant) })),
    planned: state.planned.map(swap),
    candidates: state.candidates.map(swap),
  };
}
