import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { DeckInput } from '../deck/deck.types';
import { STUB_DECK_INPUT } from '../deck/stub/stub-restaurants';
import { SearchResultStore } from '../search/search-result.store';
import { SearchService } from '../search/search.service';
import { HomeComponent } from './home.component';
import { HOME_TEXTS } from './home.texts';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let root: HTMLElement;
  let searchEnd: Subject<DeckInput>;
  let searchSpy: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const input = () => root.querySelector('input[type="text"]') as HTMLInputElement;
  const button = () => root.querySelector('button[type="submit"]') as HTMLButtonElement;
  const form = () => root.querySelector('form') as HTMLFormElement;
  const alert = () => root.querySelector('[role="alert"]');
  const status = () => root.querySelector('[role="status"]');

  async function create(): Promise<void> {
    fixture = TestBed.createComponent(HomeComponent);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  async function type(value: string): Promise<void> {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function clickSubmit(): Promise<void> {
    button().click();
    await fixture.whenStable();
  }

  /** Pressing Enter in a text field of a form makes the browser fire `submit` on the form. */
  async function pressEnter(): Promise<void> {
    form().dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    searchEnd = new Subject<DeckInput>();
    searchSpy = vi.fn(() => searchEnd.asObservable());
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([]), { provide: SearchService, useValue: { search: searchSpy } }],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await create();
  });

  describe('User Story 1 - start a search (U4, U5, U6, U7)', () => {
    it('U4: hands the unchanged sentence to the search when "Commencer" is clicked', async () => {
      await type('  asiatique près d’Opéra  ');
      await clickSubmit();

      expect(searchSpy).toHaveBeenCalledTimes(1);
      expect(searchSpy).toHaveBeenCalledWith('  asiatique près d’Opéra  ');
    });

    it('U4: submits exactly as the button when Enter is pressed', async () => {
      await type('végétarien à Opéra');
      await pressEnter();

      expect(searchSpy).toHaveBeenCalledTimes(1);
      expect(searchSpy).toHaveBeenCalledWith('végétarien à Opéra');
    });

    it('U7: the field is empty on every opening', async () => {
      expect(input().value).toBe('');
      await type('quelque chose');

      await create();
      expect(input().value).toBe('');
    });

    it.each([[''], ['   '], ['\t \n']])(
      'U5: does not submit an empty or space-only sentence (%j) and shows the message',
      async (sentence) => {
        await type(sentence);
        await clickSubmit();

        expect(searchSpy).not.toHaveBeenCalled();
        expect(alert()?.textContent?.trim()).toBe(HOME_TEXTS.emptyError);
      },
    );

    it('U5: hides the empty-search message once the user types', async () => {
      await clickSubmit();
      expect(alert()).not.toBeNull();

      await type('a');
      expect(alert()).toBeNull();
    });

    it('U6: shows progress, disables the button and ignores a second submission while searching', async () => {
      await type('asiatique');
      await clickSubmit();

      expect(status()?.textContent?.trim()).toBe(HOME_TEXTS.searching);
      expect(button().disabled).toBe(true);

      await pressEnter();
      expect(searchSpy).toHaveBeenCalledTimes(1);
    });

    it('U6: ends the in-progress state when the search ends and keeps the field text', async () => {
      await type('asiatique');
      await clickSubmit();

      searchEnd.next(STUB_DECK_INPUT);
      searchEnd.complete();
      await fixture.whenStable();

      expect(status()).toBeNull();
      expect(button().disabled).toBe(false);
      expect(input().value).toBe('asiatique');
    });
  });

  describe('Opening the deck (spec 003 FR-019, FR-020)', () => {
    it('T1: keeps the search result and opens the deck when the search succeeds', async () => {
      await type("italien ce soir près d'Opéra");
      await clickSubmit();

      searchEnd.next(STUB_DECK_INPUT);
      searchEnd.complete();
      await fixture.whenStable();

      expect(TestBed.inject(SearchResultStore).result()).toBe(STUB_DECK_INPUT);
      expect(navigate).toHaveBeenCalledWith(['/deck']);
    });

    it('opens the deck on the first result, then hands the updated one to the store without navigating again', async () => {
      await type("italien ce soir près d'Opéra");
      await clickSubmit();
      const updated = { ...STUB_DECK_INPUT };

      searchEnd.next(STUB_DECK_INPUT);
      searchEnd.next(updated);
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledTimes(1);
      expect(TestBed.inject(SearchResultStore).result()).toBe(updated);
      expect(TestBed.inject(SearchResultStore).predecessorOf(updated)).toBe(STUB_DECK_INPUT);
    });

    it('T2: stays on the home screen with the in-progress indication before the search answers', async () => {
      await type("italien ce soir près d'Opéra");
      await clickSubmit();

      expect(navigate).not.toHaveBeenCalled();
      expect(TestBed.inject(SearchResultStore).result()).toBeNull();
      expect(status()?.textContent?.trim()).toBe(HOME_TEXTS.searching);
      expect(button().disabled).toBe(true);
    });

    it('T5: stays on the home screen and shows an error message when the search fails', async () => {
      await type("italien ce soir près d'Opéra");
      await clickSubmit();

      searchEnd.error(new Error('unavailable'));
      await fixture.whenStable();

      expect(navigate).not.toHaveBeenCalled();
      expect(alert()?.textContent?.trim()).toBe(HOME_TEXTS.searchError);
      expect(status()).toBeNull();
      expect(button().disabled).toBe(false);
    });

    it('T5: hides the search error message once the user types', async () => {
      await type('italien');
      await clickSubmit();
      searchEnd.error(new Error('unavailable'));
      await fixture.whenStable();

      await type('italien ce soir');
      expect(alert()).toBeNull();
    });
  });

  describe('User Story 2 - understand Nappe at first glance (U1, U2, U3)', () => {
    const text = (selector: string) => root.querySelector(selector)?.textContent?.trim();
    const tiles = () => Array.from(root.querySelectorAll('.tile')) as HTMLElement[];

    it('U1: shows the texts of spec FR-001 word for word', () => {
      // Literals are copied from the spec on purpose: they guard home.texts.ts against drift (SC-001).
      expect(text('.logo')).toBe('Nappe');
      expect(text('.badge')).toBe('Restaurants autour de vous');
      expect(text('h1')).toBe('Où manger, juste à côté ?');
      expect(root.querySelectorAll('h1').length).toBe(1);
      expect(text('.subtitle')).toBe(
        'Trouvez un restaurant adapté à votre régime et à vos envies, en quelques secondes.',
      );
      expect(input().placeholder).toBe('Dites-nous ce qui vous ferait plaisir, et où');
      expect(button().textContent?.trim()).toBe('Commencer');
    });

    it('U2: shows the four dietary tiles', () => {
      expect(tiles().map((tile) => tile.textContent?.trim())).toEqual([
        'Vegan',
        'Végétarien',
        'Sans gluten',
        'Halal, casher…',
      ]);
    });

    it('U2: tiles are decorative: clicking them changes nothing and they are not focusable', async () => {
      await type('asiatique');
      for (const tile of tiles()) {
        tile.click();
        await fixture.whenStable();

        expect(tile.tagName).not.toBe('BUTTON');
        expect(tile.hasAttribute('tabindex')).toBe(false);
      }

      expect(searchSpy).not.toHaveBeenCalled();
      expect(input().value).toBe('asiatique');
      expect(alert()).toBeNull();
      expect(status()).toBeNull();
    });

    it('U3: shows no "Se connecter" button', () => {
      expect(root.textContent).not.toContain('Se connecter');
    });
  });
  describe('Rotating title phrase (every 5 seconds)', () => {
    const phrase = () => root.querySelector('h1 .rotating')?.textContent?.trim();

    async function tick(ms: number): Promise<void> {
      vi.advanceTimersByTime(ms);
      await fixture.whenStable();
    }

    beforeEach(async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
      fixture.destroy();
      await create();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('shows the first phrase at opening and keeps the full title as accessible name', () => {
      expect(phrase()).toBe(HOME_TEXTS.titlePhrases[0]);
      expect(root.querySelector('h1')?.getAttribute('aria-label')).toBe(HOME_TEXTS.title);
    });

    it('changes the phrase after 5 seconds, not before', async () => {
      await tick(4999);
      expect(phrase()).toBe(HOME_TEXTS.titlePhrases[0]);
      await tick(1);
      expect(phrase()).toBe(HOME_TEXTS.titlePhrases[1]);
    });

    it('goes back to the first phrase after the last one', async () => {
      await tick(5000 * HOME_TEXTS.titlePhrases.length);
      expect(phrase()).toBe(HOME_TEXTS.titlePhrases[0]);
    });

    it('stops the timer when the screen is destroyed', () => {
      fixture.destroy();
      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
