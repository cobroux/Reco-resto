import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  untracked,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CardEnrichmentStore } from '../enrichment/card-enrichment.store';
import { canRewind, currentCard, position } from './deck-state';
import { CookingSceneComponent } from './cooking-scene/cooking-scene.component';
import { DeckStore } from './deck.store';
import { DECK_TEXTS } from './deck.texts';
import { ReactionKind } from './deck.types';
import { vibrateFor } from './haptics';
import { SwipeCardComponent } from './swipe-card/swipe-card.component';
import { SwipeDirection } from './swipe-card/swipe-gesture';

type Direction = Exclude<SwipeDirection, 'none'>;

const REACTION_BY_SWIPE: Record<Direction, ReactionKind> = {
  left: 'disliked',
  right: 'liked',
  up: 'picked',
};

const SWIPE_BY_REACTION: Record<ReactionKind, Direction> = {
  disliked: 'left',
  liked: 'right',
  picked: 'up',
};

/** Swipe deck screen (feature 003). */
@Component({
  selector: 'app-deck',
  imports: [SwipeCardComponent, CookingSceneComponent, RouterLink],
  templateUrl: './deck.component.html',
  styleUrl: './deck.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckComponent implements OnInit {
  protected readonly store = inject(DeckStore);
  private readonly router = inject(Router);
  /** Website photos of the cards (spec 007); kept apart from the deck state, which it never changes. */
  protected readonly enrichment = inject(CardEnrichmentStore);

  protected readonly texts = DECK_TEXTS;
  /** Reaction buttons, in screen order (FR-004, D4). */
  protected readonly actions = [
    { kind: 'disliked', label: DECK_TEXTS.dislike, symbol: '✕', primary: false },
    { kind: 'liked', label: DECK_TEXTS.like, symbol: '♥', primary: true },
    { kind: 'picked', label: DECK_TEXTS.pick, symbol: '→', primary: false },
  ] as const;
  protected readonly state = this.store.state;
  protected readonly title = computed(() =>
    DECK_TEXTS.title(this.store.input()?.momentLabel ?? null),
  );
  protected readonly targetDateTime = computed(() => this.store.input()?.targetDateTime ?? '');
  /** Zero or one card; a new card component per restaurant so each one rises in (D18). */
  protected readonly cards = computed(() => {
    const state = this.state();
    const card = state && state.status !== 'finished' ? currentCard(state) : undefined;
    return card ? [card] : [];
  });
  private readonly cardComponent = viewChild(SwipeCardComponent);
  protected readonly position = computed(() => (this.state() ? position(this.state()!) : 0));
  protected readonly size = computed(() => this.state()?.size ?? 0);
  protected readonly canRewind = computed(() => {
    const state = this.state();
    return !!state && canRewind(state) && !this.store.pending();
  });

  constructor() {
    // Spec 007 (FR-009, FR-010): once the deck is shown, ask for the photos of its cards, current card
    // first, then the cards to come, then those already seen; again whenever refinement changes them (T22).
    effect(() => {
      const state = this.state();
      if (!state) {
        return;
      }
      const shown = state.shown.map((card) => card.restaurant).reverse();
      untracked(() =>
        this.enrichment.request([...shown.slice(0, 1), ...state.planned, ...shown.slice(1)]),
      );
    });
  }

  ngOnInit(): void {
    this.store.start();
  }

  /** Buttons fly the card off like a swipe (D18); the reaction is applied when it has left. */
  protected react(kind: ReactionKind): void {
    if (this.store.pending()) {
      return;
    }
    const direction = SWIPE_BY_REACTION[kind];
    const card = this.cardComponent();
    if (card) {
      card.leave(direction);
    }
  }

  /** A reaction was chosen: immediate haptic feedback (D19). */
  protected onSwiped(direction: Direction): void {
    vibrateFor(REACTION_BY_SWIPE[direction]);
  }

  protected onLeft(direction: Direction): void {
    this.store.react(REACTION_BY_SWIPE[direction]);
  }

  protected openList(): void {
    void this.router.navigate(['/results']);
  }
}
