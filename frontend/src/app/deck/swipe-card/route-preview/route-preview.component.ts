import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { googleMapsRouteUrl } from '../../../restaurant/google-maps';
import { Restaurant } from '../../../restaurant/restaurant';
import { DECK_TEXTS } from '../../deck.texts';
import { RoutePreview, RoutePreviewService } from '../../route-preview.service';

type Status = 'loading' | 'loaded' | 'failed';

/**
 * Route preview inside the card back (spec 004, US2). Viewing it is not a reaction (FR-006): it
 * never talks to the deck store.
 */
@Component({
  selector: 'app-route-preview',
  templateUrl: './route-preview.component.html',
  styleUrl: './route-preview.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoutePreviewComponent implements OnInit {
  readonly restaurant = input.required<Restaurant>();

  private readonly service = inject(RoutePreviewService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly texts = DECK_TEXTS;
  protected readonly mapsUrl = computed(() => googleMapsRouteUrl(this.restaurant()));
  protected readonly status = signal<Status>('loading');
  protected readonly preview = signal<RoutePreview>({ modes: [] });

  ngOnInit(): void {
    const subscription = this.service.preview(this.restaurant()).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.status.set('loaded');
      },
      error: () => this.status.set('failed'),
    });
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
  }
}
