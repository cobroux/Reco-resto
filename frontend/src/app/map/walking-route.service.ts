import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, timeout } from 'rxjs';
import { Position } from '../search/search-result';
import { MAP_CONFIG } from './map-config.token';

/** The service refuses matrices above 100 points by default (OSRM `max-table-size`): the user plus 99 places. */
export const MAX_WALKING_DISTANCES = 99;

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

/**
 * Applies the shared policy of every call to the routing service: give up after `timeoutMs`, and answer
 * `fallback()` on any failure, so the callers never see an error.
 */
function withFallback<A, R>(
  source: Observable<A>,
  timeoutMs: number,
  parse: (answer: A) => R,
  fallback: () => R,
): Observable<R> {
  return source.pipe(
    timeout(timeoutMs),
    map(parse),
    catchError(() => of(fallback())),
  );
}

/** Walking route between two points, from the routing service. */
@Injectable({ providedIn: 'root' })
export class WalkingRouteService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(MAP_CONFIG);

  /** Emits once: the route, or null when the service does not answer or finds no route (the map then falls back). */
  route(from: Position, to: Position): Observable<WalkingRoute | null> {
    const points = `${from.longitude},${from.latitude};${to.longitude},${to.latitude}`;
    return withFallback(
      this.http.get<OsrmAnswer>(`${this.config.walkingBaseUrl}/route/v1/foot/${points}`, {
        params: { overview: 'full', geometries: 'geojson' },
      }),
      this.config.walkingTimeoutMs,
      (answer): WalkingRoute | null => {
        const route = answer.code === 'Ok' ? answer.routes?.[0] : undefined;
        return route
          ? {
              coordinates: route.geometry.coordinates,
              distanceMeters: route.distance,
              durationSeconds: route.duration,
            }
          : null;
      },
      () => null,
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
    return withFallback(
      this.http.get<OsrmTableAnswer>(`${this.config.walkingBaseUrl}/table/v1/foot/${points}`, {
        params: { sources: '0', annotations: 'distance' },
      }),
      this.config.walkingTimeoutMs,
      (answer) => {
        const row = answer.code === 'Ok' ? answer.distances?.[0] : undefined;
        if (!row || row.length !== asked.length + 1) {
          return unknown();
        }
        return to.map((_, i) => (i < asked.length ? row[i + 1] : null));
      },
      unknown,
    );
  }
}
