import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { DeckStore } from '../deck/deck.store';
import { DECK_TEXTS } from '../deck/deck.texts';
import { STUB_RESTAURANTS } from '../deck/stub/stub-restaurants';
import { SessionReactionsService } from '../session/session-reactions.service';
import { ChoiceComponent } from './choice.component';

describe('ChoiceComponent', () => {
  let returnFromPick: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function render(id: string, status = 'picked') {
    returnFromPick = vi.fn();
    TestBed.configureTestingModule({
      imports: [ChoiceComponent],
      providers: [
        provideRouter([]),
        { provide: DeckStore, useValue: { returnFromPick, state: () => ({ status }) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id }) } },
        },
      ],
    });
    TestBed.inject(SessionReactionsService).set(STUB_RESTAURANTS, []);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChoiceComponent);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('shows only the requested restaurant, decided', async () => {
    const { root } = await render('stub-02');

    expect(root.querySelectorAll('[data-testid="top-card"]')).toHaveLength(1);
    expect(root.querySelector('.card-name')?.textContent).toContain(STUB_RESTAURANTS[1].name);
    expect(root.querySelector('.badge')?.textContent).toContain(DECK_TEXTS.pick);
  });

  it('links the itinerary to the route screen', async () => {
    const { root } = await render('stub-02');

    const link = root.querySelector('[data-testid="itinerary"]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/route/stub-02');
  });

  it('changes mind through the store', async () => {
    const { root } = await render('stub-02');

    (root.querySelector('[data-testid="change-mind"]') as HTMLButtonElement).click();

    expect(returnFromPick).toHaveBeenCalled();
  });

  it('toggles the detail with the weekly hours', async () => {
    const { fixture, root } = await render('stub-01');
    expect(root.querySelector('[data-testid="detail"]')).toBeNull();

    (root.querySelector('.detail-btn') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(root.querySelector('[data-testid="detail-address"]')).not.toBeNull();
    expect(root.querySelectorAll('.hours-row')).toHaveLength(7);
  });

  it('redirects to the deck when the restaurant is unknown', async () => {
    const { root } = await render('unknown');

    expect(navigate).toHaveBeenCalledWith(['/deck']);
    expect(root.querySelector('[data-testid="top-card"]')).toBeNull();
  });

  it('redirects to the deck when no restaurant is picked', async () => {
    const { root } = await render('stub-02', 'browsing');

    expect(navigate).toHaveBeenCalledWith(['/deck']);
    expect(root.querySelector('[data-testid="top-card"]')).toBeNull();
  });
});
