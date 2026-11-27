import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  PendingTasks,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import type maplibregl from 'maplibre-gl';
import { RESULTS_TEXTS } from '../deck/deck.texts';
import { formatDistance } from '../deck/format/distance';
import { Position } from '../search/search-result';
import { MAP_CONFIG } from './map-config.token';
import { buildMapStyle, readMapColors } from './map-style';
import { WalkingRoute, WalkingRouteService } from './walking-route.service';

/** Zoom used when only the restaurant is known (no position of the user). */
const SINGLE_POINT_ZOOM = 16;
const FIT_PADDING = 48;
const FIT_MAX_ZOOM = 17;

const toLngLat = (position: Position): [number, number] => [position.longitude, position.latitude];

/**
 * Map of one restaurant: its point with its name, the position of the user when known, and a dotted line between
 * them (specs/005-results-map-screen). Flat style of the Claude Design mockup, see `map-style.ts`.
 */
@Component({
  selector: 'app-restaurant-map',
  template: `
    @if (failed()) {
      <p class="unavailable" role="status" data-testid="map-unavailable">{{ unavailableText }}</p>
    }
    @if (shownCaption(); as text) {
      <span class="caption" data-testid="map-caption">{{ text }}</span>
    }
    <button
      type="button"
      class="recenter"
      data-testid="recenter-map"
      [attr.aria-label]="recenterLabel()"
      [title]="recenterLabel()"
      (click)="recenter()"
    >
      <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
        <circle cx="12" cy="12" r="3.5" fill="currentColor" />
        <circle cx="12" cy="12" r="7.5" fill="none" stroke="currentColor" stroke-width="2" />
        <path
          d="M12 1.5v4M12 18.5v4M1.5 12h4M18.5 12h4"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
        />
      </svg>
    </button>
  `,
  styleUrl: './restaurant-map.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'img', '[attr.aria-label]': 'label()' },
})
export class RestaurantMapComponent {
  readonly restaurant = input.required<Position>();
  /** Name shown on the pill above the point of the restaurant. */
  readonly name = input('');
  /** Null when the position of the user is unknown (refused): the map is then centred on the restaurant. */
  readonly user = input<Position | null>(null);
  /** Text of the pill at the bottom left while no walking route is known (the distance); no pill when empty. */
  readonly caption = input('');
  readonly label = input('');
  /** Accessible name of the button that brings the map back to the restaurant and the user. */
  readonly recenterLabel = input('');

  private readonly mapContainer = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly pendingTasks = inject(PendingTasks);
  private readonly routes = inject(WalkingRouteService);
  private readonly config = inject(MAP_CONFIG);
  protected readonly unavailableText = RESULTS_TEXTS.mapUnavailable;
  /** True when MapLibre or the first map data could not be loaded: a message replaces the map. */
  protected readonly failed = signal(false);
  /** Walking route from the user to the restaurant; null while it is computed or when it is unavailable. */
  private readonly route = signal<WalkingRoute | null>(null);
  /** Framing input: flips once when the route first arrives (and back when it is reset), not on every redraw. */
  private readonly hasRoute = computed(() => this.route() !== null);
  /** "5 min à pied · 350 m" once the route is known, else the caption given by the parent. */
  protected readonly shownCaption = computed(() => {
    const route = this.route();
    if (!route) {
      return this.caption();
    }
    return RESULTS_TEXTS.walk(
      Math.max(1, Math.round(route.durationSeconds / 60)),
      formatDistance(route.distanceMeters),
    );
  });
  /** MapLibre is loaded on demand: it is heavy and only the results screen needs it. */
  private lib: typeof maplibregl | undefined;
  private map: maplibregl.Map | undefined;
  private ready = false;
  private destroyed = false;
  private markers: maplibregl.Marker[] = [];

  constructor() {
    afterNextRender(() => this.pendingTasks.run(() => this.start()));
    effect((onCleanup) => {
      const restaurant = this.restaurant();
      const user = this.user();
      this.route.set(null);
      if (!user) {
        return;
      }
      const subscription = this.routes
        .route(user, restaurant)
        .subscribe((route) => this.route.set(route));
      onCleanup(() => subscription.unsubscribe());
    });
    effect(() => {
      this.restaurant();
      this.user();
      this.name();
      this.route();
      this.draw();
    });
    // The camera only moves when the framing inputs change: the restaurant, the user, or the first arrival of the
    // route. A redraw alone (e.g. the name) never moves it, so it does not undo a pan or a zoom of the user.
    effect(() => {
      this.restaurant();
      this.user();
      this.hasRoute();
      untracked(() => {
        if (this.ready) {
          this.recenter();
        }
      });
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.markers.forEach((marker) => marker.remove());
      this.map?.remove();
    });
  }

  private async start(): Promise<void> {
    let lib: typeof maplibregl;
    try {
      ({ default: lib } = await import('maplibre-gl'));
    } catch {
      this.failed.set(true);
      return;
    }
    if (this.destroyed) {
      return;
    }
    this.lib = lib;
    try {
      this.map = new lib.Map({
        container: this.mapContainer.nativeElement,
        style: buildMapStyle(this.config.tilesUrl, readMapColors(this.mapContainer.nativeElement)),
        center: toLngLat(this.restaurant()),
        zoom: SINGLE_POINT_ZOOM,
        attributionControl: { compact: true },
      });
    } catch {
      // No WebGL, for example.
      this.failed.set(true);
      return;
    }
    this.map.addControl(new lib.NavigationControl({ showCompass: false }), 'top-right');
    this.collapseAttribution();
    // Before the first load, an error means the style or the tiles cannot be had: nothing useful can be shown. Later,
    // a missing tile only leaves a hole in the ground, which the markers and the route still make usable.
    this.map.on('error', () => {
      if (!this.ready) {
        this.failed.set(true);
      }
    });
    this.map.on('load', () => {
      this.collapseAttribution();
      this.ready = true;
      this.draw();
      this.recenter();
    });
  }

  /** The attribution is required but starts closed (only its "i" button shows); a click opens it. */
  private collapseAttribution(): void {
    const attribution = this.mapContainer.nativeElement.querySelector('.maplibregl-ctrl-attrib');
    attribution?.classList.remove('maplibregl-compact-show');
    attribution?.removeAttribute('open');
  }

  private draw(): void {
    const map = this.map;
    if (!map || !this.ready) {
      return;
    }
    const restaurant = this.restaurant();
    const user = this.user();
    this.markers.forEach((marker) => marker.remove());
    this.markers = [this.restaurantMarker(restaurant).addTo(map)];
    if (user) {
      this.markers.push(this.userMarker(user).addTo(map));
    }
    (map.getSource('route') as maplibregl.GeoJSONSource).setData({
      type: 'FeatureCollection',
      features: user
        ? [
            {
              type: 'Feature',
              properties: {},
              geometry: { type: 'LineString', coordinates: this.path(user, restaurant) },
            },
          ]
        : [],
    });
  }

  /** The walking route when known, else a straight line (fallback while it is computed or unavailable). */
  private path(user: Position, restaurant: Position): [number, number][] {
    const route = this.route();
    return route
      ? route.coordinates.map(([lng, lat]) => [lng, lat])
      : [toLngLat(user), toLngLat(restaurant)];
  }

  private restaurantMarker(position: Position): maplibregl.Marker {
    const element = document.createElement('div');
    element.className = 'restaurant-marker';
    if (this.name()) {
      const pill = document.createElement('span');
      pill.className = 'restaurant-name';
      pill.textContent = this.name();
      element.append(pill);
    }
    return new this.lib!.Marker({ element }).setLngLat(toLngLat(position));
  }

  private userMarker(position: Position): maplibregl.Marker {
    const element = document.createElement('div');
    element.className = 'user-marker';
    return new this.lib!.Marker({ element }).setLngLat(toLngLat(position));
  }

  /** Brings the map back to the restaurant alone, or to the restaurant and the user together. */
  protected recenter(): void {
    const map = this.map;
    if (!map) {
      return;
    }
    const restaurant = toLngLat(this.restaurant());
    const user = this.user();
    if (!user) {
      map.easeTo({ center: restaurant, zoom: SINGLE_POINT_ZOOM });
      return;
    }
    const bounds = new this.lib!.LngLatBounds(toLngLat(user), restaurant);
    this.path(user, this.restaurant()).forEach((point) => bounds.extend(point));
    map.fitBounds(bounds, { padding: FIT_PADDING, maxZoom: FIT_MAX_ZOOM });
  }
}
