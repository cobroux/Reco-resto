import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { DECK_TEXTS } from '../deck/deck.texts';
import { DeckStore } from '../deck/deck.store';
import { STUB_RESTAURANTS } from '../deck/stub/stub-restaurants';
import { SessionReactionsService } from '../session/session-reactions.service';
import { RoutePlaceholderComponent } from './route-placeholder.component';

describe('RoutePlaceholderComponent (temporary, feature 006)', () => {
  let root: HTMLElement;
  let returnFromPick: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    returnFromPick = vi.fn();
    TestBed.configureTestingModule({
      imports: [RoutePlaceholderComponent],
      providers: [
        provideRouter([]),
        { provide: DeckStore, useValue: { returnFromPick } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'stub-02' }) } },
        },
      ],
    });
    TestBed.inject(SessionReactionsService).set(STUB_RESTAURANTS, []);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(RoutePlaceholderComponent);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  const button = (label: string) =>
    [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!;

  it('shows the picked restaurant name (D13)', () => {
    expect(root.querySelector('h1')?.textContent).toContain(STUB_RESTAURANTS[1].name);
  });

  it('goes back to the deck through the store (FR-014)', () => {
    button(DECK_TEXTS.backToDeck).click();

    expect(returnFromPick).toHaveBeenCalled();
  });

  it('opens the results screen (FR-008)', () => {
    button(DECK_TEXTS.seeList).click();

    expect(navigate).toHaveBeenCalledWith(['/results']);
  });
});
