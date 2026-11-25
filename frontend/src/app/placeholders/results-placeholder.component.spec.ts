import { TestBed } from '@angular/core/testing';
import { DECK_TEXTS, REACTION_LABELS } from '../deck/deck.texts';
import { STUB_RESTAURANTS } from '../deck/stub/stub-restaurants';
import { SessionReactionsService } from '../session/session-reactions.service';
import { ResultsPlaceholderComponent } from './results-placeholder.component';

describe('ResultsPlaceholderComponent (temporary, feature 005)', () => {
  it('shows its title and the reactions recorded in the session (D13, FR-011)', async () => {
    TestBed.inject(SessionReactionsService).set(STUB_RESTAURANTS, [
      { restaurantId: 'stub-01', kind: 'liked' },
      { restaurantId: 'stub-02', kind: 'disliked' },
    ]);
    const fixture = TestBed.createComponent(ResultsPlaceholderComponent);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent?.trim()).toBe(DECK_TEXTS.resultsTitle);
    const items = [...root.querySelectorAll('li')].map((li) =>
      li.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(items).toEqual([
      `${STUB_RESTAURANTS[0].name} — ${REACTION_LABELS.liked}`,
      `${STUB_RESTAURANTS[1].name} — ${REACTION_LABELS.disliked}`,
    ]);
  });
});
