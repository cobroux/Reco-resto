import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { RefinementService } from './deck/refinement.service';
import { StubRefinementService } from './deck/stub/stub-refinement.service';
import { SearchService } from './search/search.service';
import { StubSearchService } from './search/stub-search.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Feature 002 replaces these providers with the real search and refinement.
    { provide: SearchService, useClass: StubSearchService },
    { provide: RefinementService, useClass: StubRefinementService },
  ],
};
