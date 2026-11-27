import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SearchResultStore } from '../search/search-result.store';

/** The deck opens only after a search; otherwise the home screen opens (spec 003 FR-021). */
export const searchResultGuard: CanActivateFn = () =>
  inject(SearchResultStore).result() ? true : inject(Router).createUrlTree(['/']);
