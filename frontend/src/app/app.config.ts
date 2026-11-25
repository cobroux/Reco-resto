import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { SearchService } from './search/search.service';
import { StubSearchService } from './search/stub-search.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Feature 002 replaces this provider with the real search.
    { provide: SearchService, useClass: StubSearchService },
  ],
};
