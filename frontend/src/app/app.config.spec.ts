import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { RoutePreviewService } from './deck/route-preview.service';
import { StubRoutePreviewService } from './deck/stub/stub-route-preview.service';
import { HttpSearchService } from './search/http-search.service';
import { SearchService } from './search/search.service';

describe('appConfig', () => {
  it('provides the real HTTP search (feature 002) as the search service', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(SearchService)).toBeInstanceOf(HttpSearchService);
  });

  it('provides the stub route preview of the card back (spec 004) until feature 006', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(RoutePreviewService)).toBeInstanceOf(StubRoutePreviewService);
  });
});
