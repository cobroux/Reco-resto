import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DECK_TEXTS } from '../../deck/deck.texts';
import { RestaurantView } from '../restaurant-view';

/** Expanded detail of a restaurant: address, phone and weekly hours (targeted day highlighted). */
@Component({
  selector: 'app-restaurant-detail',
  templateUrl: './restaurant-detail.component.html',
  styleUrl: './restaurant-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestaurantDetailComponent {
  readonly item = input.required<RestaurantView>();

  protected readonly texts = DECK_TEXTS;
}
