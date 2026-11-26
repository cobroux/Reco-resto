import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { RefinementService } from './deck/refinement.service';
import { RoutePreviewService } from './deck/route-preview.service';
import { StubRefinementService } from './deck/stub/stub-refinement.service';
import { StubRoutePreviewService } from './deck/stub/stub-route-preview.service';
import { HttpSearchService } from './search/http-search.service';
import { SearchService } from './search/search.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    // Feature 002: the real search. The refinement of the deck (feature 003) is still a stub.
    { provide: SearchService, useClass: HttpSearchService },
    { provide: RefinementService, useClass: StubRefinementService },
    // Spec 004: route preview of the card back, a stub until feature 006 provides the real routing.
    { provide: RoutePreviewService, useClass: StubRoutePreviewService },
  ],
};
