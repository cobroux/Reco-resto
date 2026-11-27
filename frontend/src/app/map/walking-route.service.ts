import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, timeout } from 'rxjs';
import { Position } from '../search/search-result';

/**
 * OSRM with a pedestrian profile, run by the OpenStreetMap community (no key, no guaranteed availability).
 * Chosen by the team on 2026-11-27 for the results map; to be replaced before a production release.
 */
export const WALKING_ROUTE_URL = 'https://routing.openstreetmap.de/routed-foot/route/v1/foot';
/** Same service and profile, matrix endpoint: walking distances from one point to many, in one request. */
export const WALKING_TABLE_URL = 'https://routing.openstreetmap.de/routed-foot/table/v1/foot';

/** The service refuses matrices above 100 points by default (OSRM `max-table-size`): the user plus 99 places. */
export const MAX_WALKING_DISTANCES = 99;

/** How long to wait for the routing service before falling back to a straight line (value to be validated). */
export const WALKING_ROUTE_TIMEOUT_MS = 8000;

/** A walking route: the path to draw ([longitude, latitude] points), its length and its duration. */
export interface WalkingRoute {
  readonly coordinates: readonly (readonly [number, number])[];
  readonly distanceMeters: number;
  readonly durationSeconds: number;
}

interface OsrmTableAnswer {
  readonly code?: string;
  readonly distances?: readonly (readonly (number | null)[])[];
}

interface OsrmAnswer {
  readonly code?: string;
  readonly routes?: readonly {
    readonly distance: number;
    readonly duration: number;
    readonly geometry: { readonly coordinates: readonly (readonly [number, number])[] };
  }[];
}

/** Walking route between two points, from the routing service. */
@Injectable({ providedIn: 'root' })
export class WalkingRouteService {
  private readonly http = inject(HttpClient);

  /** Emits once: the route, or null when the service does not answer or finds no route (the map then falls back). */
  route(from: Position, to: Position): Observable<WalkingRoute | null> {
    const points = `${from.longitude},${from.latitude};${to.longitude},${to.latitude}`;
    return this.http
      .get<OsrmAnswer>(`${WALKING_ROUTE_URL}/${points}`, {
        params: { overview: 'full', geometries: 'geojson' },
      })
      .pipe(
        timeout(WALKING_ROUTE_TIMEOUT_MS),
        map((answer) => {
          const route = answer.code === 'Ok' ? answer.routes?.[0] : undefined;
          return route
            ? {
                coordinates: route.geometry.coordinates,
                distanceMeters: route.distance,
                durationSeconds: route.duration,
              }
            : null;
        }),
        catchError(() => of(null)),
      );
  }

  /**
   * Walking distances in metres from `from` to each of `to`, in the same order (at most `MAX_WALKING_DISTANCES`:
   * the places beyond get null). A null is a place the service cannot reach; the whole answer is all-null when the
   * service does not answer, so the caller keeps its own distances.
   */
  distances(from: Position, to: readonly Position[]): Observable<(number | null)[]> {
    const asked = to.slice(0, MAX_WALKING_DISTANCES);
    const unknown = () => to.map(() => null);
    if (asked.length === 0) {
      return of([]);
    }
    const points = [from, ...asked].map((p) => `${p.longitude},${p.latitude}`).join(';');
    return this.http
      .get<OsrmTableAnswer>(`${WALKING_TABLE_URL}/${points}`, {
        params: { sources: '0', annotations: 'distance' },
      })
      .pipe(
        timeout(WALKING_ROUTE_TIMEOUT_MS),
        map((answer) => {
          const row = answer.code === 'Ok' ? answer.distances?.[0] : undefined;
          if (!row || row.length !== asked.length + 1) {
            return unknown();
          }
          return to.map((_, i) => (i < asked.length ? row[i + 1] : null));
        }),
        catchError(() => of(unknown())),
      );
  }
}
