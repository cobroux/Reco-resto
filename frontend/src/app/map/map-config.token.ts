import { InjectionToken } from '@angular/core';

/** External services and limits of the results map (specs/005-results-map-screen, "Assumptions" and "Privacy"). */
export interface MapConfig {
  /** Vector tiles of OpenFreeMap (OpenStreetMap data, free, no API key); its TileJSON carries the attribution. */
  readonly tilesUrl: string;
  /**
   * OSRM with a pedestrian profile, run by the OpenStreetMap community (no key, no guaranteed availability).
   * Chosen by the team on 2026-11-27 for the MVP; to be replaced before a production release.
   * The user's exact position, up to 99 restaurant positions and the user's IP address reach this third party.
   */
  readonly walkingBaseUrl: string;
  /** How long to wait for the routing service before falling back (value to be validated by the team). */
  readonly walkingTimeoutMs: number;
}

/** Same pattern as `DECK_SIZE`: the default lives here, a test or a deployment overrides the token. */
export const MAP_CONFIG = new InjectionToken<MapConfig>('MAP_CONFIG', {
  factory: () => ({
    tilesUrl: 'https://tiles.openfreemap.org/planet',
    walkingBaseUrl: 'https://routing.openstreetmap.de/routed-foot',
    walkingTimeoutMs: 8000,
  }),
});
