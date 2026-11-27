import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { Position } from '../search/search-result';
import { mapCalls, resetMapCalls } from './maplibre-gl.mock';
import { MAP_COLORS, MAP_STYLE, TILES_URL } from './map-style';
import { RestaurantMapComponent } from './restaurant-map.component';
import { WalkingRoute, WalkingRouteService } from './walking-route.service';

vi.mock('maplibre-gl', async () => (await import('./maplibre-gl.mock')).maplibreMock);

const RESTAURANT: Position = { latitude: 48.8566, longitude: 2.3522 };
const USER: Position = { latitude: 48.8606, longitude: 2.3376 };

@Component({
  imports: [RestaurantMapComponent],
  template: `<app-restaurant-map
    [restaurant]="restaurant()"
    [name]="name()"
    [user]="user()"
    [caption]="caption()"
    label="Carte test"
    recenterLabel="Recentrer"
  />`,
})
class HostComponent {
  readonly restaurant = signal(RESTAURANT);
  readonly name = signal('Trattoria Verde');
  readonly user = signal<Position | null>(USER);
  readonly caption = signal('350 m');
}

describe('RestaurantMapComponent', () => {
  let routeAnswer: Subject<WalkingRoute | null>;
  const routeCalls: unknown[][] = [];

  beforeEach(() => {
    resetMapCalls();
    routeCalls.length = 0;
    routeAnswer = new Subject();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: WalkingRouteService,
          useValue: {
            route: (...args: unknown[]) => {
              routeCalls.push(args);
              return routeAnswer;
            },
          },
        },
      ],
    });
  });

  async function create() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  const click = (root: HTMLElement) =>
    root.querySelector<HTMLElement>('[data-testid="recenter-map"]')!.click();

  it('starts a map centred on the restaurant and exposes its label', async () => {
    const { root } = await create();

    expect(mapCalls.maps.length).toBe(1);
    expect(mapCalls.maps[0].options['center']).toEqual([RESTAURANT.longitude, RESTAURANT.latitude]);
    expect(root.querySelector('app-restaurant-map')?.getAttribute('aria-label')).toBe('Carte test');
  });

  it('shows the restaurant with its name and the user, and draws the line between them', async () => {
    await create();

    const [restaurant, user] = mapCalls.markers;
    expect(restaurant.element.className).toBe('restaurant-marker');
    expect(restaurant.element.querySelector('.restaurant-name')?.textContent).toBe('Trattoria Verde');
    expect(restaurant.lngLat).toEqual([RESTAURANT.longitude, RESTAURANT.latitude]);
    expect(user.element.className).toBe('user-marker');
    expect(user.lngLat).toEqual([USER.longitude, USER.latitude]);
    expect(mapCalls.routes.at(-1)).toEqual({
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          properties: {},
          geometry: {
            type: 'LineString',
            coordinates: [
              [USER.longitude, USER.latitude],
              [RESTAURANT.longitude, RESTAURANT.latitude],
            ],
          },
        },
      ],
    });
  });

  describe('walking route', () => {
    const ROUTE: WalkingRoute = {
      coordinates: [
        [2.3376, 48.8606],
        [2.34, 48.859],
        [2.3522, 48.8566],
      ],
      distanceMeters: 1256.5,
      durationSeconds: 1007.5,
    };

    it('asks for the route from the user to the restaurant', async () => {
      await create();

      expect(routeCalls).toEqual([[USER, RESTAURANT]]);
    });

    it('draws a straight line and the distance until the route arrives', async () => {
      const { root } = await create();

      expect(mapCalls.routes.at(-1)).toMatchObject({
        features: [{ geometry: { coordinates: [[USER.longitude, USER.latitude], [RESTAURANT.longitude, RESTAURANT.latitude]] } }],
      });
      expect(root.querySelector('[data-testid="map-caption"]')?.textContent).toBe('350 m');
    });

    it('draws the route and shows its duration and distance when it arrives', async () => {
      const { fixture, root } = await create();
      mapCalls.fitBounds = [];

      routeAnswer.next(ROUTE);
      await fixture.whenStable();

      expect(mapCalls.routes.at(-1)).toMatchObject({ features: [{ geometry: { coordinates: ROUTE.coordinates } }] });
      expect(root.querySelector('[data-testid="map-caption"]')?.textContent).toBe('17 min à pied · 1,3 km');
      expect(mapCalls.fitBounds.length).toBeGreaterThan(0);
    });

    it('keeps the straight line and the distance when no route is found', async () => {
      const { fixture, root } = await create();

      routeAnswer.next(null);
      await fixture.whenStable();

      expect(root.querySelector('[data-testid="map-caption"]')?.textContent).toBe('350 m');
    });

    it('asks for no route when the position of the user is unknown', async () => {
      const fixture = TestBed.createComponent(HostComponent);
      fixture.componentInstance.user.set(null);
      await fixture.whenStable();

      expect(routeCalls).toEqual([]);
    });
  });

  it('shows only the restaurant, without line, when the position of the user is unknown', async () => {
    const { fixture } = await create();

    mapCalls.markers = [];
    fixture.componentInstance.user.set(null);
    await fixture.whenStable();

    expect(mapCalls.markers.map((marker) => marker.element.className)).toEqual(['restaurant-marker']);
    expect(mapCalls.routes.at(-1)).toEqual({ type: 'FeatureCollection', features: [] });
  });

  it('collapses the attribution control at start', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    const attribution = document.createElement('details');
    attribution.className = 'maplibregl-ctrl-attrib maplibregl-compact maplibregl-compact-show';
    attribution.setAttribute('open', '');
    (fixture.nativeElement as HTMLElement).querySelector('app-restaurant-map')!.append(attribution);

    await fixture.whenStable();

    expect(attribution.classList.contains('maplibregl-compact-show')).toBe(false);
    expect(attribution.hasAttribute('open')).toBe(false);
  });

  it('shows the caption pill, and none when the caption is empty', async () => {
    const { fixture, root } = await create();

    expect(root.querySelector('[data-testid="map-caption"]')?.textContent).toBe('350 m');

    fixture.componentInstance.caption.set('');
    await fixture.whenStable();

    expect(root.querySelector('[data-testid="map-caption"]')).toBeNull();
  });

  it('removes the map when destroyed', async () => {
    const { fixture } = await create();

    fixture.destroy();

    expect(mapCalls.removedMaps).toBe(1);
  });

  describe('recenter button', () => {
    it('is labelled', async () => {
      const { root } = await create();

      expect(root.querySelector('[data-testid="recenter-map"]')?.getAttribute('aria-label')).toBe('Recentrer');
    });

    it('fits the restaurant and the user again when the user is known', async () => {
      const { root } = await create();
      mapCalls.fitBounds = [];

      click(root);

      expect(mapCalls.fitBounds.length).toBe(1);
    });

    it('centres on the restaurant when the position of the user is unknown', async () => {
      const { fixture, root } = await create();
      fixture.componentInstance.user.set(null);
      await fixture.whenStable();
      mapCalls.easeTo = [];

      click(root);

      expect(mapCalls.easeTo).toEqual([[{ center: [RESTAURANT.longitude, RESTAURANT.latitude], zoom: 16 }]]);
    });
  });
});

describe('MAP_STYLE', () => {
  it('uses the OpenFreeMap tiles and the flat colours of the mockup', () => {
    expect(MAP_STYLE.sources['openmaptiles']).toEqual({ type: 'vector', url: TILES_URL });
    const ground = MAP_STYLE.layers.find((layer) => layer.id === 'ground');
    expect(ground?.paint).toEqual({ 'background-color': MAP_COLORS.ground });
    const roads = MAP_STYLE.layers.filter((layer) => layer.id.startsWith('roads-'));
    expect(roads.length).toBe(2);
    roads.forEach((layer) => expect(layer.paint?.['line-color' as never]).toBe(MAP_COLORS.road));
  });
});
