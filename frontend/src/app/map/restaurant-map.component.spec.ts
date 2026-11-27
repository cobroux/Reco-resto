import { ApplicationRef, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { Position } from '../search/search-result';
import { mapCalls, resetMapCalls } from './maplibre-gl.mock';
import { MapColors, buildMapStyle, readMapColors } from './map-style';
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

  it('shows a message instead of the map when the tiles cannot be loaded', async () => {
    mapCalls.holdLoad = true;
    const { root } = await create();
    expect(root.querySelector('[data-testid="map-unavailable"]')).toBeNull();

    mapCalls.maps[0].emit('error');
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.tick();

    expect(root.querySelector('[data-testid="map-unavailable"]')?.textContent).toContain('carte');
  });

  it('shows the message when the map cannot be created', async () => {
    mapCalls.failConstruct = true;
    const { root } = await create();
    TestBed.tick();

    expect(root.querySelector('[data-testid="map-unavailable"]')).not.toBeNull();
  });

  it('keeps the map when an error comes after it loaded', async () => {
    const { root } = await create();

    mapCalls.maps[0].emit('error');
    TestBed.tick();

    expect(root.querySelector('[data-testid="map-unavailable"]')).toBeNull();
  });

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
    expect(restaurant.element.querySelector('.restaurant-name')?.textContent).toBe(
      'Trattoria Verde',
    );
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
        features: [
          {
            geometry: {
              coordinates: [
                [USER.longitude, USER.latitude],
                [RESTAURANT.longitude, RESTAURANT.latitude],
              ],
            },
          },
        ],
      });
      expect(root.querySelector('[data-testid="map-caption"]')?.textContent).toBe('350 m');
    });

    it('draws the route and shows its duration and distance when it arrives', async () => {
      const { fixture, root } = await create();
      mapCalls.fitBounds = [];

      routeAnswer.next(ROUTE);
      await fixture.whenStable();

      expect(mapCalls.routes.at(-1)).toMatchObject({
        features: [{ geometry: { coordinates: ROUTE.coordinates } }],
      });
      expect(root.querySelector('[data-testid="map-caption"]')?.textContent).toBe(
        '17 min à pied · 1,3 km',
      );
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

    expect(mapCalls.markers.map((marker) => marker.element.className)).toEqual([
      'restaurant-marker',
    ]);
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

  describe('camera', () => {
    it('fits the view once at start', async () => {
      await create();

      expect(mapCalls.fitBounds.length).toBe(1);
    });

    it('does not move on a pure redraw (name change)', async () => {
      const { fixture } = await create();
      mapCalls.fitBounds = [];

      fixture.componentInstance.name.set('Another name');
      await fixture.whenStable();

      expect(mapCalls.fitBounds).toEqual([]);
      expect(mapCalls.easeTo).toEqual([]);
    });

    it('does not move when no route is found', async () => {
      const { fixture } = await create();
      mapCalls.fitBounds = [];

      routeAnswer.next(null);
      await fixture.whenStable();

      expect(mapCalls.fitBounds).toEqual([]);
    });

    it('fits again when the restaurant changes', async () => {
      const { fixture } = await create();
      mapCalls.fitBounds = [];

      fixture.componentInstance.restaurant.set({ latitude: 48.86, longitude: 2.35 });
      await fixture.whenStable();

      expect(mapCalls.fitBounds.length).toBeGreaterThan(0);
    });
  });

  describe('recenter button', () => {
    it('is labelled', async () => {
      const { root } = await create();

      expect(root.querySelector('[data-testid="recenter-map"]')?.getAttribute('aria-label')).toBe(
        'Recentrer',
      );
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

      expect(mapCalls.easeTo).toEqual([
        [{ center: [RESTAURANT.longitude, RESTAURANT.latitude], zoom: 16 }],
      ]);
    });
  });
});

describe('buildMapStyle', () => {
  const COLORS: MapColors = {
    ground: '#111111',
    building: '#222222',
    park: '#333333',
    water: '#444444',
    road: '#555555',
    route: '#666666',
  };

  it('uses the given tiles and colours, ', () => {
    const style = buildMapStyle('https://tiles.test', COLORS);

    expect(style.sources['openmaptiles']).toEqual({ type: 'vector', url: 'https://tiles.test' });
    const ground = style.layers.find((layer) => layer.id === 'ground');
    expect(ground?.paint).toEqual({ 'background-color': COLORS.ground });
    const roads = style.layers.filter((layer) => layer.id.startsWith('roads-'));
    expect(roads.length).toBe(2);
    roads.forEach((layer) => expect(layer.paint?.['line-color' as never]).toBe(COLORS.road));
  });
});

describe('readMapColors', () => {
  it('reads the colours from the design tokens', () => {
    const element = document.createElement('div');
    element.style.setProperty('--map-ground', ' #aaa ');
    element.style.setProperty('--color-primary', '#bbb');
    document.body.append(element);

    const colors = readMapColors(element);
    element.remove();

    expect(colors.ground).toBe('#aaa');
    expect(colors.route).toBe('#bbb');
  });
});
