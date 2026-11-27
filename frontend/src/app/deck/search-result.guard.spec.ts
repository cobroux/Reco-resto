import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { SearchResultStore } from '../search/search-result.store';
import { STUB_DECK_INPUT } from './stub/stub-restaurants';
import { searchResultGuard } from './search-result.guard';

@Component({ template: 'home' })
class FakeHome {}

@Component({ template: 'deck' })
class FakeDeck {}

describe('searchResultGuard (FR-021)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: FakeHome },
          { path: 'deck', component: FakeDeck, canActivate: [searchResultGuard] },
        ]),
      ],
    });
  });

  it('T4: opens the home screen when the deck is opened without a search', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/deck');

    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.textContent).toBe('home');
  });

  it('opens the deck once a search result exists', async () => {
    TestBed.inject(SearchResultStore).set(STUB_DECK_INPUT);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/deck');

    expect(TestBed.inject(Router).url).toBe('/deck');
  });
});
