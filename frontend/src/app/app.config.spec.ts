import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { HttpSearchService } from './search/http-search.service';
import { SearchService } from './search/search.service';

describe('appConfig', () => {
  it('provides the real HTTP search (feature 002) as the search service', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(SearchService)).toBeInstanceOf(HttpSearchService);
  });
});
