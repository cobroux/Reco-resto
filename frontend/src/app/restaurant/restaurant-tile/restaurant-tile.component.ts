import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DECK_TEXTS, RESULTS_TEXTS } from '../../deck/deck.texts';
import { RestaurantMapComponent } from '../../map/restaurant-map.component';
import { Position } from '../../search/search-result';
import { RestaurantDetailComponent } from '../restaurant-detail/restaurant-detail.component';
import { RestaurantView } from '../restaurant-view';

/**
 * Big tile of a restaurant (photo or tablecloth, badge, name, chips, facts, detail toggle).
 * The page projects its own action buttons, shown before the detail toggle.
 */
@Component({
  selector: 'app-restaurant-tile',
  templateUrl: './restaurant-tile.component.html',
  styleUrl: './restaurant-tile.component.css',
  imports: [RestaurantDetailComponent, RestaurantMapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestaurantTileComponent {
  readonly item = input.required<RestaurantView>();
  /** True once the restaurant is chosen: the badge reads "On y va" instead of the suggestion label. */
  readonly decided = input(false);
  readonly expanded = input(false);
  /** Device position of the search; drawn on the map, shown when the restaurant has no photo but a location. */
  readonly userPosition = input<Position | null>(null);
  readonly toggleDetail = output<void>();

  protected readonly texts = DECK_TEXTS;
  protected readonly results = RESULTS_TEXTS;
}
