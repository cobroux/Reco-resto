import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DECK_TEXTS, RESULTS_TEXTS } from '../../deck/deck.texts';
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
  imports: [RestaurantDetailComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestaurantTileComponent {
  readonly item = input.required<RestaurantView>();
  /** True once the restaurant is chosen: the badge reads "On y va" instead of the suggestion label. */
  readonly decided = input(false);
  readonly expanded = input(false);
  readonly toggleDetail = output<void>();

  protected readonly texts = DECK_TEXTS;
  protected readonly results = RESULTS_TEXTS;
}
