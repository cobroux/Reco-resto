import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, from, Observable, switchMap, throwError } from 'rxjs';
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
  'OUTSIDE_FRANCE',
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
 * `POST /api/search`.
 */
@Injectable()
export class HttpSearchService extends SearchService {
  private readonly http = inject(HttpClient);

  search(sentence: string): Observable<SearchResult> {
    return from(currentPosition()).pipe(
      switchMap((position) =>
        this.http.post<SearchResult>('/api/search', position ? { sentence, position } : { sentence }),
      ),
      catchError((error: unknown) => throwError(() => failureOf(error))),
    );
  }
}
