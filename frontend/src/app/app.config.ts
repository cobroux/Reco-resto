import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { DeckSource } from './deck/deck-source';
import { RefinementService } from './deck/refinement.service';
import { StubDeckSource } from './deck/stub/stub-deck-source';
import { StubRefinementService } from './deck/stub/stub-refinement.service';
import { SearchService } from './search/search.service';
import { StubSearchService } from './search/stub-search.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Feature 002 replaces these providers with the real search and refinement.
    { provide: SearchService, useClass: StubSearchService },
    { provide: DeckSource, useClass: StubDeckSource },
    { provide: RefinementService, useClass: StubRefinementService },
  ],
};
