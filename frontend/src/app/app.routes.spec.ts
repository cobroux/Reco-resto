import { DeckComponent } from './deck/deck.component';
import { searchResultGuard } from './deck/search-result.guard';
import { HomeComponent } from './home/home.component';
import { ResultsPlaceholderComponent } from './placeholders/results-placeholder.component';
import { RoutePlaceholderComponent } from './placeholders/route-placeholder.component';
import { routes } from './app.routes';

describe('routes (D14, plan "Navigation")', () => {
  const componentOf = (path: string) => routes.find((r) => r.path === path)?.component;

  it('serves the home screen at the root', () => {
    expect(componentOf('')).toBe(HomeComponent);
  });

  it('serves the swipe deck at /deck', () => {
    expect(componentOf('deck')).toBe(DeckComponent);
  });

  it('T4: guards the deck so it opens only after a search (FR-021)', () => {
    expect(routes.find((r) => r.path === 'deck')?.canActivate).toEqual([searchResultGuard]);
  });

  it('serves the temporary results and route screens', () => {
    expect(componentOf('results')).toBe(ResultsPlaceholderComponent);
    expect(componentOf('route/:id')).toBe(RoutePlaceholderComponent);
  });
});
