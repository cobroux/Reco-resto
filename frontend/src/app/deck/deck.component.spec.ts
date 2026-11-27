import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { vi } from 'vitest';
import { Restaurant } from '../restaurant/restaurant';
import { SearchResultStore } from '../search/search-result.store';
import { SessionReactionsService } from '../session/session-reactions.service';
import { CARD_ENRICHMENT_URL } from '../enrichment/card-enrichment.store';
import { COOKING_TOSS_MS } from './cooking-toss.token';
import { DECK_SIZE } from './deck-size.token';
import { DeckComponent } from './deck.component';
import { DeckStore } from './deck.store';
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
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RefinementService, useValue: refinement },
        { provide: SWIPE_ANIMATION_MS, useValue: animationMs },
        { provide: COOKING_TOSS_MS, useValue: 0 },
      ],
    });
    TestBed.inject(SearchResultStore).set(input);
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
      expect((bar as HTMLElement).style.getPropertyValue('--segments')).toBe('15');
    });

    it('does not show the "Vos coups de cœur" panel nor the card back (FR-012)', async () => {
      await create();

      expect(text()).not.toContain('coups de cœur');
      expect(text()).not.toContain('Voir la fiche');
    });
  });

  describe('D20 — tablecloth under the stack', () => {
    const cardsInStack = () => root.querySelectorAll('.stack > app-swipe-card');

    it('lays the card stack on a tablecloth (AS6, FR-015)', async () => {
      await create();

      expect(root.querySelector('.stack [data-testid="table"]')).not.toBeNull();
    });

    it('shows no card behind the current card (FR-015)', async () => {
      await create();

      expect(cardsInStack().length).toBe(1);
    });

    it('shows a crêpe tossed from a pan with "En cuisine…" under the card (AS7, FR-017, D21)', async () => {
      await create();
      const scene = root.querySelector('.stack [data-testid="cooking"]');

      expect(scene?.textContent?.trim()).toBe('En cuisine…');
      expect(scene?.querySelector('.pan')).not.toBeNull();
      expect(scene?.querySelector('.crepe')).not.toBeNull();
      expect(scene?.getAttribute('role')).toBe('status');
      expect(scene?.getAttribute('aria-hidden')).toBe('true');
    });

    it('shows no card behind while a refinement is pending (FR-015)', async () => {
      await create();
      const pending = new Subject<readonly Restaurant[]>();
      vi.spyOn(refinement, 'refine').mockReturnValue(pending);
      await click(button(DECK_TEXTS.like));

      expect(cardsInStack().length).toBe(1);
      pending.next([]);
    });
  });

  describe('US2 — react to each card', () => {
    beforeEach(() => create());

    it('dislikes with "✕" labelled "Pas miam" (AS1, D4)', async () => {
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

    it('picks with "→" labelled "On y va" and opens the choice screen (AS3, D4)', async () => {
      expect(button(DECK_TEXTS.pick).textContent).toContain('→');
      await click(button(DECK_TEXTS.pick));

      expect(navigate).toHaveBeenCalledWith(['/choice', 'stub-01']);
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

  describe('spec 004 — card back', () => {
    beforeEach(() => create());

    const flipButton = () =>
      root.querySelector('app-swipe-card button.flip-btn') as HTMLButtonElement;

    it('keeps the reaction buttons enabled while the back is shown (FR-008)', async () => {
      await click(flipButton());

      expect(root.querySelector('[data-testid="back"]')).not.toBeNull();
      for (const name of [DECK_TEXTS.dislike, DECK_TEXTS.like, DECK_TEXTS.pick]) {
        expect(button(name).disabled, name).toBe(false);
      }
    });

    it('applies a reaction made on the back to this restaurant, then shows the next front', async () => {
      await click(flipButton());
      await click(button(DECK_TEXTS.like));

      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind: 'liked' }]);
      expect(cardName()).toBe(STUB_RESTAURANTS[1].name);
      expect(root.querySelector('[data-testid="back"]')).toBeNull();
    });
  });

  describe('spec 004 — logo', () => {
    it('is a link to the home page, without confirmation', async () => {
      await create();
      const logo = root.querySelector('header a.logo');

      expect(logo?.getAttribute('href')).toBe('/');
      expect(logo?.textContent).toContain(DECK_TEXTS.logo);
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
  describe('spec 007 — card website enrichment (T4, T5, T15, T17-T20, T22, T23, T29)', () => {
    /** Browser image preloaded by the enrichment store: loads, unless its address contains "broken". */
    class FakeImage {
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      referrerPolicy = '';
      set src(url: string) {
        queueMicrotask(() => (url.includes('broken') ? this.onerror?.() : this.onload?.()));
      }
    }

    const website = (index: number) => `https://resto-${index}.example/`;
    const ENRICHABLE: DeckInput = {
      ...STUB_DECK_INPUT,
      restaurants: STUB_RESTAURANTS.map((r, i) => ({
        ...r,
        photoUrl: undefined,
        website: website(i),
      })),
    };
    let http: HttpTestingController;
    /** Websites requested since the previous call (`match` hands each request once). */
    const sent = () =>
      http.match((r) => r.url === CARD_ENRICHMENT_URL).map((r) => r.request.params.get('website'));
    const photo = () => root.querySelector('app-swipe-card img.photo')?.getAttribute('src');
    const tablecloth = () => root.querySelector('app-swipe-card [data-testid="tablecloth"]');

    async function answer(index: number, body: object | 'error'): Promise<void> {
      const [request] = http.match(
        (r) => r.url === CARD_ENRICHMENT_URL && r.params.get('website') === website(index),
      );
      if (body === 'error') {
        request.flush(null, { status: 500, statusText: 'Server Error' });
      } else {
        request.flush(body);
      }
      await sleep(0);
      await fixture.whenStable();
    }

    async function start(input = ENRICHABLE): Promise<void> {
      await create(input);
      http = TestBed.inject(HttpTestingController);
    }

    beforeEach(() => vi.stubGlobal('Image', FakeImage));
    afterEach(() => vi.unstubAllGlobals());

    it('opens the deck with tablecloths while every request is pending, current card first (T17, T18, T27)', async () => {
      await start();

      expect(cardName()).toBe(STUB_RESTAURANTS[0].name);
      expect(tablecloth()).not.toBeNull();
      expect(sent()).toEqual([website(0), website(1), website(2)]);
    });

    it('sends no request for a restaurant with a data-source photo, which is kept (T4)', async () => {
      await start({
        ...STUB_DECK_INPUT,
        restaurants: STUB_RESTAURANTS.map((r, i) => ({
          ...r,
          photoUrl: 'stub/restaurant.svg',
          website: website(i),
        })),
      });

      expect(sent()).toEqual([]);
      expect(photo()).toBe(STUB_RESTAURANTS[0].photoUrl);
    });

    it('shows the photo in place, same card, position, reactions and deck state (T5, T19, SC-003)', async () => {
      await start();
      const deck = TestBed.inject(DeckStore);
      const before = deck.state();

      await answer(0, {
        photoUrl: 'https://cdn.example/salle-0.jpg',
        photoOrigin: 'SHARING_IMAGE',
      });

      expect(photo()).toBe('https://cdn.example/salle-0.jpg');
      expect(cardName()).toBe(STUB_RESTAURANTS[0].name);
      expect(text()).toContain('1 sur 15');
      expect(reactions()).toEqual([]);
      expect(deck.state()).toBe(before);
    });

    it('keeps a reaction made before the answer and shows the photo on rewind (T20, T23)', async () => {
      await start();
      await click(button(DECK_TEXTS.like));

      await answer(0, {
        photoUrl: 'https://cdn.example/salle-0.jpg',
        photoOrigin: 'SHARING_IMAGE',
      });

      expect(reactions()).toEqual([{ restaurantId: 'stub-01', kind: 'liked' }]);
      expect(cardName()).toBe(STUB_RESTAURANTS[1].name);
      await click(button(DECK_TEXTS.rewind));
      expect(cardName()).toBe(STUB_RESTAURANTS[0].name);
      expect(photo()).toBe('https://cdn.example/salle-0.jpg');
    });

    it('gives each card its own result: card 2 failing does not stop cards 1 and 3 (T29)', async () => {
      await start();
      await answer(0, {
        photoUrl: 'https://cdn.example/salle-0.jpg',
        photoOrigin: 'SHARING_IMAGE',
      });
      await answer(1, 'error');
      await answer(2, {
        photoUrl: 'https://cdn.example/salle-2.jpg',
        photoOrigin: 'LARGEST_PAGE_IMAGE',
      });

      expect(photo()).toBe('https://cdn.example/salle-0.jpg');
      await click(button(DECK_TEXTS.like));
      expect(tablecloth()).not.toBeNull();
      await click(button(DECK_TEXTS.like));
      expect(photo()).toBe('https://cdn.example/salle-2.jpg');
      expect(text()).toContain('3 sur 15');
    });

    it('requests a restaurant brought by refinement (T22)', async () => {
      TestBed.configureTestingModule({ providers: [{ provide: DECK_SIZE, useValue: 3 }] });
      await start();
      await answer(0, {});
      await answer(1, {});
      await answer(2, {});
      vi.spyOn(refinement, 'refine').mockReturnValue(of([ENRICHABLE.restaurants[10]]));

      await click(button(DECK_TEXTS.like));

      expect(cardName()).toBe(STUB_RESTAURANTS[10].name);
      expect(sent()).toEqual([website(10)]);
    });

    it('opens the website without reaction nor move: same card 3, same 2 reactions (T15, FR-008)', async () => {
      await start();
      await click(button(DECK_TEXTS.like));
      await click(button(DECK_TEXTS.dislike));
      const link = root.querySelector('app-swipe-card a.website') as HTMLAnchorElement;
      link.addEventListener('click', (event) => event.preventDefault());

      await click(link);

      expect(cardName()).toBe(STUB_RESTAURANTS[2].name);
      expect(text()).toContain('3 sur 15');
      expect(reactions()).toEqual([
        { restaurantId: 'stub-01', kind: 'liked' },
        { restaurantId: 'stub-02', kind: 'disliked' },
      ]);
    });
  });
});
