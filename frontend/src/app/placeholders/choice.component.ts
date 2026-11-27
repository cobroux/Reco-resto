import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DeckStore } from '../deck/deck.store';
import { DECK_TEXTS, RESULTS_TEXTS } from '../deck/deck.texts';
import { RestaurantTileComponent } from '../restaurant/restaurant-tile/restaurant-tile.component';
import { toRestaurantView } from '../restaurant/restaurant-view';
import { SearchResultStore } from '../search/search-result.store';
import { SessionReactionsService } from '../session/session-reactions.service';

/** Choice screen: only the restaurant picked with "On y va", shown with the shared tile. */
@Component({
  selector: 'app-choice',
  templateUrl: './choice.component.html',
  styleUrl: './choice.component.css',
  imports: [RouterLink, RestaurantTileComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChoiceComponent {
  private readonly searchResult = inject(SearchResultStore);
  protected readonly store = inject(DeckStore);
  protected readonly texts = DECK_TEXTS;
  protected readonly results = RESULTS_TEXTS;
  protected readonly expanded = signal(false);

  /** Device position of the search, shown on the map of the tile. */
  protected readonly userPosition = computed(() => this.searchResult.result()?.userPosition ?? null);

  protected readonly id =inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  protected readonly item = (() => {
    const restaurant = inject(SessionReactionsService).restaurant(this.id);
    if (this.store.state()?.status !== 'picked') {
      return null;
    }
    const targetDateTime = this.searchResult.result()?.targetDateTime ?? '';
    return restaurant ? toRestaurantView(restaurant, targetDateTime) : null;
  })();

  constructor() {
    if (!this.item) {
      // Unknown id, empty session or no pending pick (e.g. page reload, direct access): back to the deck.
      void inject(Router).navigate(['/deck']);
    }
  }

  protected toggleDetail(): void {
    this.expanded.update((value) => !value);
  }
}
