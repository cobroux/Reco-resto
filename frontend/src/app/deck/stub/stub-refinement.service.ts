import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { Restaurant } from '../../restaurant/restaurant';
import { RefinementRequest, RefinementService } from '../refinement.service';

/**
 * Temporary refinement, replaced by feature 002. Keeps the initial order (no real refinement);
 * `failing` simulates an unavailable service (FR-013).
 */
@Injectable()
export class StubRefinementService extends RefinementService {
  failing = false;

  override refine(request: RefinementRequest): Observable<readonly Restaurant[]> {
    if (this.failing) {
      return throwError(() => new Error('Refinement unavailable (stub)'));
    }
    const excluded = new Set(request.excludedIds);
    return of(request.candidates.filter((r) => !excluded.has(r.id)).slice(0, request.slots));
  }
}
