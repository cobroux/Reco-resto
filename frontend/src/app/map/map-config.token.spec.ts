import { TestBed } from '@angular/core/testing';
import { MAP_CONFIG } from './map-config.token';

describe('MAP_CONFIG', () => {
  it('defaults to OpenFreeMap tiles, the community walking OSRM and an 8 s timeout (to be validated)', () => {
    expect(TestBed.inject(MAP_CONFIG)).toEqual({
      tilesUrl: 'https://tiles.openfreemap.org/planet',
      walkingBaseUrl: 'https://routing.openstreetmap.de/routed-foot',
      walkingTimeoutMs: 8000,
    });
  });

  it('can be overridden', () => {
    const config = { tilesUrl: 'https://t', walkingBaseUrl: 'https://w', walkingTimeoutMs: 1 };
    TestBed.configureTestingModule({ providers: [{ provide: MAP_CONFIG, useValue: config }] });
    expect(TestBed.inject(MAP_CONFIG)).toBe(config);
  });
});
