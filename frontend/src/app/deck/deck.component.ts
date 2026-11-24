import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { canRewind, currentCard, position } from './deck-state';
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
  imports: [SwipeCardComponent],
  templateUrl: './deck.component.html',
  styleUrl: './deck.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckComponent implements OnInit {
  protected readonly store = inject(DeckStore);
  private readonly router = inject(Router);

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
  protected readonly hasNext = computed(() => (this.state()?.planned.length ?? 0) > 0);
  protected readonly canRewind = computed(() => {
    const state = this.state();
    return !!state && canRewind(state) && !this.store.pending();
  });

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
