import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  EMPTY,
  catchError,
  concat,
  filter,
  from,
  map,
  Observable,
  switchMap,
  throwError,
} from 'rxjs';
import { DeckInput } from '../deck/deck.types';
import { WalkingRouteService } from '../map/walking-route.service';
import { toDeckInput } from './deck-input';
import { Position, SearchErrorCode, SearchFailure, SearchResult } from './search-result';
import { SearchService } from './search.service';

/**
 * How long to wait for the device position before searching without it.
 * The 5 s value is an unverified placeholder: to be validated by the team.
 */
export const POSITION_TIMEOUT_MS = 5000;

const ERROR_CODES: readonly SearchErrorCode[] = [
  'NOT_UNDERSTOOD',
  'NO_RESULT',
  'PLACE_REQUIRED',
  'SERVICE_UNAVAILABLE',
];

/** The device position, or null when it is refused or unavailable (spec 002, FR-006). */
export function currentPosition(): Promise<Position | null> {
  if (typeof navigator === 'undefined' || !('geolocation' in navigator)) {
    return Promise.resolve(null);
  }
  return new Promise((resolve) =>
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      () => resolve(null),
      { timeout: POSITION_TIMEOUT_MS },
    ),
  );
}

/** The code of the backend error body; anything else (network down, 5xx, unknown body) is a service failure. */
function failureOf(error: unknown): SearchFailure {
  const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
  return new SearchFailure(ERROR_CODES.includes(code) ? code : 'SERVICE_UNAVAILABLE');
}

/**
 * Real search (feature 002): sends the sentence, unchanged, and the device position when granted to
 * `POST /api/search`, and hands the result to the swipe deck as a `DeckInput` (feature 003).
 */
@Injectable()
export class HttpSearchService extends SearchService {
  private readonly http = inject(HttpClient);
  private readonly walking = inject(WalkingRouteService);

  search(sentence: string): Observable<DeckInput> {
    return from(currentPosition()).pipe(
      switchMap((position) =>
        this.http
          .post<SearchResult>('/api/search', position ? { sentence, position } : { sentence })
          .pipe(
            map((result) => toDeckInput(result, new Date(), position)),
            // The deck must not wait for the routing service (a third party): the first emission carries the distances
            // of the backend, the second one, when the routing service answers, the walking distances.
            switchMap((input) => concat([input], this.withWalkingDistances(input))),
          ),
      ),
      catchError((error: unknown) => throwError(() => failureOf(error))),
    );
  }

  /**
   * Emits, at most once, the input with the straight-line distance of each restaurant replaced by its walking
   * distance, the one the map shows, so that the deck, the results and the map give the same distance. A restaurant
   * without coordinates, or that the routing service does not answer for, keeps the distance of the backend; nothing
   * is emitted when no distance changes.
   */
  private withWalkingDistances(input: DeckInput): Observable<DeckInput> {
    const user = input.userPosition;
    const located = input.restaurants.filter((restaurant) => restaurant.location);
    if (!user || located.length === 0) {
      return EMPTY;
    }
    return this.walking
      .distances(
        user,
        located.map((restaurant) => restaurant.location!),
      )
      .pipe(
        filter((distances) => distances.some((meters) => meters != null)),
        map((distances) => {
          // Keyed by the restaurant itself, not by its id: two restaurants can share an id (fallback id name|address).
          const walking = new Map(located.map((restaurant, i) => [restaurant, distances[i]]));
          return {
            ...input,
            restaurants: input.restaurants.map((restaurant) => {
              const meters = walking.get(restaurant);
              return meters == null ? restaurant : { ...restaurant, distanceMeters: meters };
            }),
          };
        }),
      );
  }
}
