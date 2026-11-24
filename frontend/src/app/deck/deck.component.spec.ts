import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { vi } from 'vitest';
import { Restaurant } from '../restaurant/restaurant';
import { SessionReactionsService } from '../session/session-reactions.service';
import { DeckSource } from './deck-source';
import { DeckComponent } from './deck.component';
import { DECK_TEXTS } from './deck.texts';
import { DeckInput } from './deck.types';
import { RefinementService } from './refinement.service';
import { StubRefinementService } from './stub/stub-refinement.service';
import { STUB_DECK_INPUT, STUB_RESTAURANTS } from './stub/stub-restaurants';
import { SWIPE_ANIMATION_MS, SwipeCardComponent } from './swipe-card/swipe-card.component';

describe('DeckComponent', () => {
  let fixture: ComponentFixture<DeckComponent>;
  let root: HTMLElement;
  let refinement: StubRefinementService;
  let navigate: ReturnType<typeof vi.spyOn>;
  let vibrate: ReturnType<typeof vi.fn>;
  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  beforeEach(() => {
    vibrate = vi.fn(() => true);
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
  });

  afterEach(() => {
    delete (navigator as { vibrate?: unknown }).vibrate;
  });

  async function create(input: DeckInput = STUB_DECK_INPUT, animationMs = 0): Promise<void> {
    refinement = new StubRefinementService();
    TestBed.configureTestingModule({
      imports: [DeckComponent],
      providers: [
        provideRouter([]),
        { provide: DeckSource, useValue: { load: () => of(input) } },
        { provide: RefinementService, useValue: refinement },
        { provide: SWIPE_ANIMATION_MS, useValue: animationMs },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(DeckComponent);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const text = () => root.textContent ?? '';
  const cardName = () => root.querySelector('app-swipe-card h2')?.textContent?.trim();
  const button = (name: string) =>
    root.querySelector(`button[aria-label="${name}"]`) as HTMLButtonElement;
  const reactions = () => TestBed.inject(SessionReactionsService).reactions();

  /** Clicks, then lets the leave animation (0 ms in tests) end. */
  async function click(element: HTMLElement): Promise<void> {
    element.click();
    await fixture.whenStable();
    await sleep(0);
    await fixture.whenStable();
  }

  describe('US1 — browse the deck', () => {
    it('builds the title from the targeted moment (AS1)', async () => {
      await create();
      expect(root.querySelector('h1')?.textContent?.trim()).toBe('Ça vous dit, ce soir ?');
    });

    it('says "maintenant" when no moment is given (AS1)', async () => {
      await create({ ...STUB_DECK_INPUT, momentLabel: null });
      expect(root.querySelector('h1')?.textContent?.trim()).toBe('Ça vous dit, maintenant ?');
    });

    it('shows the first card and the progress "1 sur 15" with a progress bar (FR-001, D11)', async () => {
      await create();

      expect(cardName()).toBe(STUB_RESTAURANTS[0].name);
      expect(text()).toContain('1 sur 15');
      const bar = root.querySelector('[role="progressbar"]');
      expect(bar?.getAttribute('aria-valuenow')).toBe('1');
      expect(bar?.getAttribute('aria-valuemax')).toBe('15');
    });

    it('does not show the "Vos coups de cœur" panel nor the card back (FR-012)', async () => {
      await create();

      expect(text()).not.toContain('coups de cœur');
      expect(text()).not.toContain('Voir la fiche');
    });
  });

  describe('US2 — react to each card', () => {
    beforeEach(() => create());

    it('dislikes with "✕" labelled "Pas envie" (AS1, D4)', async () => {
      expect(button(DECK_TEXTS.dislike).textContent).toContain('✕');
      await click(button(DECK_TEXTS.dislike));

      expect(cardName()).toBe(STUB_RESTAURANTS[1].name);
      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind: 'disliked' }]);
    });

    it('likes with "♥" labelled "Miam" (AS2, D4)', async () => {
      expect(button(DECK_TEXTS.like).textContent).toContain('♥');
      await click(button(DECK_TEXTS.like));

      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind: 'liked' }]);
      expect(text()).toContain('2 sur 15');
    });

    it('picks with "→" labelled "On y va" and opens the route screen (AS3, D4)', async () => {
      expect(button(DECK_TEXTS.pick).textContent).toContain('→');
      await click(button(DECK_TEXTS.pick));

      expect(navigate).toHaveBeenCalledWith(['/route', 'stub-01']);
    });

    it('reacts to a swipe of the card', async () => {
      const card = fixture.debugElement.query((d) => d.name === 'app-swipe-card');
      card.triggerEventHandler('swiped', 'right');
      card.triggerEventHandler('left', 'right');
      await fixture.whenStable();

      expect(vibrate).toHaveBeenCalledWith(15);
      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind: 'liked' }]);
    });

    it('opens the results screen with "Voir la liste" at any time (AS5, FR-009)', async () => {
      const links = [...root.querySelectorAll('button')].filter(
        (b) => b.textContent?.trim() === DECK_TEXTS.seeList,
      );
      expect(links.length).toBeGreaterThan(0);
      await click(links[0]);

      expect(navigate).toHaveBeenCalledWith(['/results']);
    });
  });

  describe('US3 — refinement failure (FR-013)', () => {
    it('shows a discreet message when refinement fails and hides it once it works again', async () => {
      await create();
      expect(text()).not.toContain(DECK_TEXTS.degraded);

      refinement.failing = true;
      await click(button(DECK_TEXTS.like));
      expect(root.querySelector('[role="status"]')?.textContent).toContain(DECK_TEXTS.degraded);
      expect(cardName()).toBe(STUB_RESTAURANTS[1].name);

      refinement.failing = false;
      await click(button(DECK_TEXTS.like));
      expect(text()).not.toContain(DECK_TEXTS.degraded);
    });
  });

  describe('US4 — rewind', () => {
    beforeEach(() => create());

    it('has "<" labelled "Revenir à la carte précédente", disabled on the first card (AS3, D3, D4)', () => {
      const rewind = button(DECK_TEXTS.rewind);
      expect(rewind.textContent).toContain('<');
      expect(rewind.disabled).toBe(true);
    });

    it('brings back the previous card and cancels its reaction (AS1)', async () => {
      await click(button(DECK_TEXTS.like));
      await click(button(DECK_TEXTS.rewind));

      expect(cardName()).toBe(STUB_RESTAURANTS[0].name);
      expect(reactions()).toEqual([]);
      expect(button(DECK_TEXTS.rewind).disabled).toBe(true);
    });
  });
  describe('D18, D19 — animation and haptic feedback', () => {
    it('vibrates with the pattern of the reaction on a button (D19)', async () => {
      await create();
      await click(button(DECK_TEXTS.dislike));
      expect(vibrate).toHaveBeenLastCalledWith(15);

      await click(button(DECK_TEXTS.pick));
      expect(vibrate).toHaveBeenLastCalledWith([15, 60, 15]);
    });

    it('applies a button reaction only once the card has flown off (D18)', async () => {
      await create(STUB_DECK_INPUT, 30);
      button(DECK_TEXTS.like).click();
      await fixture.whenStable();

      expect(root.querySelector('app-swipe-card article')?.classList).toContain('leaving');
      expect(reactions()).toEqual([]);

      await sleep(50);
      await fixture.whenStable();
      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind: 'liked' }]);
      expect(cardName()).toBe(STUB_RESTAURANTS[1].name);
    });

    it('shows each restaurant on a new card, so the next card rises in (D18)', async () => {
      await create();
      const first = fixture.debugElement.query(
        (d) => d.name === 'app-swipe-card',
      ).componentInstance;
      await click(button(DECK_TEXTS.like));
      const second = fixture.debugElement.query(
        (d) => d.name === 'app-swipe-card',
      ).componentInstance;

      expect(second).toBeInstanceOf(SwipeCardComponent);
      expect(second).not.toBe(first);
    });
  });

  describe('D19 — iPhone haptic switch over the reaction buttons', () => {
    const hapticSwitch = (name: string) =>
      button(name).parentElement!.querySelector('input[switch]') as HTMLInputElement;

    beforeEach(() => create());

    it('covers each reaction button with a native switch hidden from assistive technologies', () => {
      for (const name of [DECK_TEXTS.dislike, DECK_TEXTS.like, DECK_TEXTS.pick]) {
        const input = hapticSwitch(name);
        expect(input, name).not.toBeNull();
        expect(input.type).toBe('checkbox');
        expect(input.getAttribute('aria-hidden')).toBe('true');
        expect(input.tabIndex).toBe(-1);
      }
    });

    it.each([
      ['dislike', 'disliked'],
      ['like', 'liked'],
      ['pick', 'picked'],
    ] as const)('a tap on the %s switch triggers the same reaction', async (text, kind) => {
      await click(hapticSwitch(DECK_TEXTS[text]));

      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind }]);
    });

    it('disables the switches while a refinement is pending', async () => {
      const pending = new Subject<readonly Restaurant[]>();
      vi.spyOn(refinement, 'refine').mockReturnValue(pending);
      await click(button(DECK_TEXTS.like));

      expect(hapticSwitch(DECK_TEXTS.like).disabled).toBe(true);
      pending.next([]);
      await fixture.whenStable();
      expect(hapticSwitch(DECK_TEXTS.like).disabled).toBe(false);
    });
  });
});
