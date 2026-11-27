import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DECK_TEXTS, RESULTS_TEXTS } from '../deck/deck.texts';
import { ReactionKind } from '../deck/deck.types';
import { formatDistance } from '../deck/format/distance';
import { closingTimeAt } from '../deck/format/opening';
import { weeklyHoursRows } from '../deck/format/weekly-hours';
import { Restaurant } from '../restaurant/restaurant';
import { SearchResultStore } from '../search/search-result.store';
import { SessionReactionsService } from '../session/session-reactions.service';

/** How long the hearts last after a choice (value of the Claude Design mockup). */
const BURST_MS = 1500;

/** Hearts of the burst: an angle, a colour and a staggered animation, as in the mockup. */
const HEARTS = Array.from({ length: 12 }, (_, i) => ({
  angle: i * 30 + 6,
  pink: i % 3 === 0,
  duration: 900 + (i % 4) * 120,
  delay: (i % 3) * 60,
}));

interface ResultItem {
  readonly id: string;
  readonly kind: ReactionKind;
  readonly restaurant: Restaurant;
  readonly distance: string;
  readonly closing: string | null;
  readonly tag: string | undefined;
  readonly hours: ReturnType<typeof weeklyHoursRows>;
}

/** Results screen: the "Miam" of the session, the choice ("On y va") and the "Pas miam" (feature 005). */
@Component({
  selector: 'app-results-placeholder',
  templateUrl: './results-placeholder.component.html',
  styleUrl: './results-placeholder.component.css',
  imports: [RouterLink, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultsPlaceholderComponent {
  private readonly session = inject(SessionReactionsService);
  private readonly searchResult = inject(SearchResultStore);

  protected readonly texts = DECK_TEXTS;
  protected readonly results = RESULTS_TEXTS;
  protected readonly hearts = HEARTS;

  protected readonly expandedId = signal<string | null>(null);
  protected readonly burstOn = signal(false);
  /** Null until the user toggles the list: it is then open only when there is no "Miam". */
  private readonly nopeToggled = signal<boolean | null>(null);
  private burstTimer: ReturnType<typeof setTimeout> | undefined;

  private readonly items = computed<readonly ResultItem[]>(() => {
    const targetDateTime = this.searchResult.result()?.targetDateTime ?? '';
    return this.session.reactions().flatMap((reaction) => {
      const restaurant = this.session.restaurant(reaction.restaurantId);
      if (!restaurant) {
        return [];
      }
      return [
        {
          id: restaurant.id,
          kind: reaction.kind,
          restaurant,
          distance: formatDistance(restaurant.distanceMeters),
          closing: closingTimeAt(restaurant.weeklyHours, targetDateTime),
          tag: restaurant.keywords[0],
          hours: weeklyHoursRows(restaurant.weeklyHours, targetDateTime),
        },
      ];
    });
  });

  /** The picked restaurant, else the first "Miam". */
  protected readonly top = computed(() => {
    const liked = this.items().filter((item) => item.kind !== 'disliked');
    return liked.find((item) => item.kind === 'picked') ?? liked[0] ?? null;
  });
  protected readonly decided = computed(() => this.top()?.kind === 'picked');
  protected readonly others = computed(() =>
    this.items().filter((item) => item.kind === 'liked' && item !== this.top()),
  );
  protected readonly nopes = computed(() => this.items().filter((item) => item.kind === 'disliked'));
  protected readonly nopeOpen = computed(() => this.nopeToggled() ?? this.top() === null);

  protected readonly title = computed(() => {
    const top = this.top();
    if (!top) {
      return RESULTS_TEXTS.emptyTitle;
    }
    if (this.decided()) {
      return RESULTS_TEXTS.decided(top.restaurant.name);
    }
    const count = this.items().filter((item) => item.kind !== 'disliked').length;
    return RESULTS_TEXTS.title(count, this.searchResult.result()?.momentLabel ?? null);
  });
  protected readonly subtitle = computed(() => {
    if (!this.top()) {
      return RESULTS_TEXTS.emptySubtitle;
    }
    return this.decided() ? RESULTS_TEXTS.decidedSubtitle : RESULTS_TEXTS.chooseSubtitle;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.burstTimer));
  }

  protected toggleDetail(id: string): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  protected toggleNope(): void {
    this.nopeToggled.set(!this.nopeOpen());
  }

  protected pick(id: string): void {
    this.session.setReaction(id, 'picked');
    this.burstOn.set(true);
    clearTimeout(this.burstTimer);
    this.burstTimer = setTimeout(() => this.burstOn.set(false), BURST_MS);
  }

  protected like(id: string): void {
    this.session.setReaction(id, 'liked');
  }

  protected dislike(id: string): void {
    this.session.setReaction(id, 'disliked');
  }
}
