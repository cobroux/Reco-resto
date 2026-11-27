import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { MAP_CONFIG } from './map-config.token';
import { MAX_WALKING_DISTANCES, WalkingRoute, WalkingRouteService } from './walking-route.service';

const FROM = { latitude: 48.8606, longitude: 2.3376 };
const TO = { latitude: 48.8566, longitude: 2.3522 };
const CONFIG = {
  tilesUrl: 'https://tiles.test',
  walkingBaseUrl: 'https://osrm.test/foot',
  walkingTimeoutMs: 1234,
};
const ROUTE_URL = `${CONFIG.walkingBaseUrl}/route/v1/foot`;
const TABLE_URL = `${CONFIG.walkingBaseUrl}/table/v1/foot`;
const URL = `${ROUTE_URL}/2.3376,48.8606;2.3522,48.8566`;

describe('WalkingRouteService', () => {
  let service: WalkingRouteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAP_CONFIG, useValue: CONFIG },
      ],
    });
    service = TestBed.inject(WalkingRouteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  const request = () =>
    http.expectOne((req) => req.url === URL && req.params.get('geometries') === 'geojson');

  it('asks for the full geometry, longitude first, and returns the route', () => {
    let route: WalkingRoute | null | undefined;
    service.route(FROM, TO).subscribe((value) => (route = value));

    const call = request();
    expect(call.request.params.get('overview')).toBe('full');
    call.flush({
      code: 'Ok',
      routes: [
        {
          distance: 1256.5,
          duration: 1007.5,
          geometry: {
            coordinates: [
              [2.3376, 48.8606],
              [2.3522, 48.8566],
            ],
          },
        },
      ],
    });

    expect(route).toEqual({
      coordinates: [
        [2.3376, 48.8606],
        [2.3522, 48.8566],
      ],
      distanceMeters: 1256.5,
      durationSeconds: 1007.5,
    });
  });

  it('answers null when the service finds no route', () => {
    let route: WalkingRoute | null | undefined;
    service.route(FROM, TO).subscribe((value) => (route = value));

    request().flush({ code: 'NoRoute' });

    expect(route).toBeNull();
  });

  it('answers null when the service fails', () => {
    let route: WalkingRoute | null | undefined;
    service.route(FROM, TO).subscribe((value) => (route = value));

    request().flush('error', { status: 500, statusText: 'Server Error' });

    expect(route).toBeNull();
  });

  it('answers null when the service is too slow', () => {
    vi.useFakeTimers();
    let route: WalkingRoute | null | undefined;
    service.route(FROM, TO).subscribe((value) => (route = value));
    request();

    vi.advanceTimersByTime(CONFIG.walkingTimeoutMs + 1);

    expect(route).toBeNull();
  });

  describe('distances', () => {
    const TABLE = `${TABLE_URL}/2.3376,48.8606;2.3522,48.8566;2.34,48.865`;
    const OTHER = { latitude: 48.865, longitude: 2.34 };
    const table = () =>
      http.expectOne((req) => req.url === TABLE && req.params.get('sources') === '0');

    it('asks one matrix request from the user to every place and returns the distances in order', () => {
      let result: (number | null)[] | undefined;
      service.distances(FROM, [TO, OTHER]).subscribe((value) => (result = value));

      const call = table();
      expect(call.request.params.get('annotations')).toBe('distance');
      call.flush({ code: 'Ok', distances: [[0, 1256.5, 617.8]] });

      expect(result).toEqual([1256.5, 617.8]);
    });

    it('keeps null for a place the service cannot reach', () => {
      let result: (number | null)[] | undefined;
      service.distances(FROM, [TO, OTHER]).subscribe((value) => (result = value));

      table().flush({ code: 'Ok', distances: [[0, null, 617.8]] });

      expect(result).toEqual([null, 617.8]);
    });

    it('answers all null when the service fails or answers badly', () => {
      const results: ((number | null)[] | undefined)[] = [];
      service.distances(FROM, [TO, OTHER]).subscribe((value) => results.push(value));
      table().flush('error', { status: 500, statusText: 'Server Error' });
      service.distances(FROM, [TO, OTHER]).subscribe((value) => results.push(value));
      table().flush({ code: 'Ok', distances: [[0, 1]] });

      expect(results).toEqual([
        [null, null],
        [null, null],
      ]);
    });

    it('asks nothing when there is no place', () => {
      let result: (number | null)[] | undefined;
      service.distances(FROM, []).subscribe((value) => (result = value));

      expect(result).toEqual([]);
      http.expectNone((req) => req.url.includes('/table/'));
    });

    it('asks for at most the places the service accepts and answers null for the others', () => {
      const places = Array.from({ length: MAX_WALKING_DISTANCES + 1 }, () => TO);
      let result: (number | null)[] | undefined;
      service.distances(FROM, places).subscribe((value) => (result = value));

      const call = http.expectOne((req) => req.url.startsWith(TABLE_URL));
      expect(call.request.url.split(';').length).toBe(MAX_WALKING_DISTANCES + 1);
      call.flush({
        code: 'Ok',
        distances: [[0, ...Array.from({ length: MAX_WALKING_DISTANCES }, () => 5)]],
      });

      expect(result?.slice(-2)).toEqual([5, null]);
      expect(result?.length).toBe(MAX_WALKING_DISTANCES + 1);
    });
  });
});
