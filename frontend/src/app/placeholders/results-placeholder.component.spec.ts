import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { provideNoWalkingRoute } from '../map/walking-route.testing';
import { DECK_TEXTS, RESULTS_TEXTS } from '../deck/deck.texts';
import { ReactionKind } from '../deck/deck.types';
import { formatDistance } from '../deck/format/distance';
import { closingTimeAt } from '../deck/format/opening';
import { STUB_DECK_INPUT, STUB_RESTAURANTS } from '../deck/stub/stub-restaurants';
import { googleMapsRouteUrl } from '../restaurant/google-maps';
import { SearchResultStore } from '../search/search-result.store';
import { SessionReactionsService } from '../session/session-reactions.service';
import { ResultsPlaceholderComponent } from './results-placeholder.component';

vi.mock('maplibre-gl', async () => (await import('../map/maplibre-gl.mock')).maplibreMock);

describe('ResultsPlaceholderComponent (results screen, mockup "Web 2c · Résultats")', () => {
  const [first, second, third, fourth, fifth] = STUB_RESTAURANTS;
  let root: HTMLElement;
  let fixture: ReturnType<typeof TestBed.createComponent<ResultsPlaceholderComponent>>;

  const q = (selector: string) => root.querySelector<HTMLElement>(selector);
  const qa = (selector: string) => [...root.querySelectorAll<HTMLElement>(selector)];
  const text = (selector: string) => q(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  async function open(reactions: [string, ReactionKind][], momentLabel: string | null = 'ce soir') {
    TestBed.inject(SearchResultStore).set({ ...STUB_DECK_INPUT, momentLabel });
    TestBed.inject(SessionReactionsService).set(
      STUB_RESTAURANTS,
      reactions.map(([restaurantId, kind]) => ({ restaurantId, kind })),
    );
    fixture = TestBed.createComponent(ResultsPlaceholderComponent);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  async function click(element: HTMLElement | null): Promise<void> {
    element!.click();
    await fixture.whenStable();
  }

  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideNoWalkingRoute()] }));

  describe('header', () => {
    beforeEach(() => open([[first.id, 'liked']]));

    it('shows the Nappe logo linking to the home page, without profile button', () => {
      const home = q('[data-testid="home-link"]')!;

      expect(home.getAttribute('href')).toBe('/');
      expect(home.getAttribute('aria-label')).toBe(DECK_TEXTS.backToHome);
      expect(home.textContent).toContain(DECK_TEXTS.logo);
      expect(home.querySelector('.logo-mark')).not.toBeNull();
      expect(q('header.header')?.firstElementChild).toBe(home);
      expect(q('header.header')?.children.length).toBe(1);
      expect(q('main')?.contains(home)).toBe(false);
    });
  });

  describe('suggestion state (liked restaurants, none picked)', () => {
    beforeEach(() =>
      open([
        [first.id, 'liked'],
        [second.id, 'disliked'],
        [third.id, 'liked'],
        [fourth.id, 'liked'],
        [fifth.id, 'disliked'],
      ]),
    );

    it('counts the "Miam" in the title and invites to choose', () => {
      expect(text('h1')).toBe('Vos 3 coups de cœur ce soir');
      expect(text('.subtitle')).toBe(RESULTS_TEXTS.chooseSubtitle);
    });

    it('leaves the moment out of the title when the search had none, and does not pluralize one', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [provideRouter([]), provideNoWalkingRoute()] });
      await open([[first.id, 'liked']], null);

      expect(text('h1')).toBe('Vos 1 coup de cœur');
    });

    it('puts the first "Miam" in the big card with the suggestion badge', () => {
      const card = q('[data-testid="top-card"]')!;

      expect(card.querySelector('.card-name')?.textContent?.trim()).toBe(first.name);
      expect(card.querySelector('.badge')?.textContent?.trim()).toBe(RESULTS_TEXTS.suggestion);
      expect(card.textContent).toContain(first.cuisine);
      expect(card.textContent).toContain(formatDistance(first.distanceMeters));
      expect(card.textContent).toContain(first.dish!.name);
      expect(card.textContent).toContain(
        closingTimeAt(first.weeklyHours, STUB_DECK_INPUT.targetDateTime)!,
      );
    });

    it('uses the Nappe gingham as image while the restaurant has no photo', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [provideRouter([]), provideNoWalkingRoute()] });
      TestBed.inject(SearchResultStore).set(STUB_DECK_INPUT);
      TestBed.inject(SessionReactionsService).set(
        [{ ...first, photoUrl: undefined }],
        [{ restaurantId: first.id, kind: 'liked' }],
      );
      fixture = TestBed.createComponent(ResultsPlaceholderComponent);
      root = fixture.nativeElement as HTMLElement;
      await fixture.whenStable();

      expect(q('[data-testid="top-card"] .tablecloth')).not.toBeNull();
      expect(q('[data-testid="top-card"] img')).toBeNull();
    });

    it('shows the map of the restaurant and the user instead of the gingham when it has a location', async () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [provideRouter([]), provideNoWalkingRoute()] });
      TestBed.inject(SearchResultStore).set({
        ...STUB_DECK_INPUT,
        userPosition: { latitude: 48.86, longitude: 2.34 },
      });
      TestBed.inject(SessionReactionsService).set(
        [{ ...first, photoUrl: undefined, location: { latitude: 48.85, longitude: 2.35 } }],
        [{ restaurantId: first.id, kind: 'liked' }],
      );
      fixture = TestBed.createComponent(ResultsPlaceholderComponent);
      root = fixture.nativeElement as HTMLElement;
      await fixture.whenStable();

      expect(q('[data-testid="top-map"]')).not.toBeNull();
      expect(q('[data-testid="top-map"]')?.getAttribute('aria-label')).toBe(
        RESULTS_TEXTS.mapLabel(first.name),
      );
      expect(q('[data-testid="top-card"] .tablecloth')).toBeNull();
      expect(q('[data-testid="top-map"] [data-testid="map-caption"]')?.textContent).toBe(
        formatDistance(first.distanceMeters),
      );
      expect(q('[data-testid="top-map"] [data-testid="recenter-map"]')).not.toBeNull();
    });

    it('lists the other "Miam" below, without "Voir à proximité"', () => {
      expect(text('.others-title')).toBe(RESULTS_TEXTS.othersChoosing);
      expect(qa('.other .other-name').map((el) => el.textContent?.trim())).toEqual([
        third.name,
        fourth.name,
      ]);
      expect(root.textContent).not.toContain('Voir à proximité');
    });

    it('moves a restaurant to "Pas miam" with the cross, and brings it back with "Finalement, miam"', async () => {
      expect(text('[data-testid="nope-toggle"]')).toContain('Pas miam · 2');

      await click(qa('.other-nope')[0]);
      expect(qa('.other .other-name').map((el) => el.textContent?.trim())).toEqual([fourth.name]);
      expect(text('[data-testid="nope-toggle"]')).toContain('Pas miam · 3');

      await click(q('[data-testid="nope-toggle"]'));
      expect(q('[data-testid="nope-toggle"]')?.getAttribute('aria-expanded')).toBe('true');
      const rows = qa('.nope-row');
      expect(rows.map((row) => row.querySelector('.nope-name')?.textContent?.trim())).toContain(
        third.name,
      );

      const row = rows.find((r) => r.textContent?.includes(third.name))!;
      await click(row.querySelector('.revive'));
      expect(text('[data-testid="nope-toggle"]')).toContain('Pas miam · 2');
      expect(qa('.other .other-name').map((el) => el.textContent?.trim())).toContain(third.name);
    });

    it('collapses the "Pas miam" list at first and toggles it', async () => {
      const toggle = q('[data-testid="nope-toggle"]')!;
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(qa('.nope-row').length).toBe(0);

      await click(toggle);
      expect(qa('.nope-row').length).toBe(2);

      await click(toggle);
      expect(qa('.nope-row').length).toBe(0);
    });

    it('expands and collapses the full detail of the suggestion with the Détail button', async () => {
      const button = () => q('[data-testid="top-card"] .detail-btn')!;
      expect(q('[data-testid="detail"]')).toBeNull();
      expect(button().textContent?.trim()).toBe(DECK_TEXTS.detail);

      await click(button());
      const detail = q('[data-testid="detail"]')!;
      expect(detail.querySelector('[data-testid="detail-address"]')?.textContent?.trim()).toBe(
        first.address,
      );
      expect(detail.querySelectorAll('.hours-row').length).toBe(7);
      expect(button().getAttribute('aria-expanded')).toBe('true');
      expect(button().textContent?.trim()).toBe(DECK_TEXTS.hideDetail);

      await click(button());
      expect(q('[data-testid="detail"]')).toBeNull();
    });

    it('expands the detail of another "Miam" from its row', async () => {
      await click(qa('.other .detail-btn')[0]);

      expect(q('.other [data-testid="detail"]')).not.toBeNull();
    });

    it('links "Refaire un tour" to the deck', () => {
      const redo = q('[data-testid="deck-link"]')!;

      expect(redo.getAttribute('href')).toBe('/deck');
      expect(redo.textContent?.trim()).toBe(RESULTS_TEXTS.redo);
    });

    describe('"On y va" on the suggestion', () => {
      beforeEach(() => click(q('[data-testid="go-top"]')));

      it('announces the decision with the Itinéraire and "Changer d’avis" buttons', () => {
        expect(text('h1')).toBe(RESULTS_TEXTS.decided(first.name));
        expect(text('.subtitle')).toBe(RESULTS_TEXTS.decidedSubtitle);
        expect(q('[data-testid="top-card"] .badge')?.textContent?.trim()).toBe(DECK_TEXTS.pick);
        const itinerary = q('[data-testid="itinerary"]');
        expect(itinerary?.getAttribute('href')).toBe(googleMapsRouteUrl(first));
        expect(itinerary?.getAttribute('target')).toBe('_blank');
        expect(q('[data-testid="go-top"]')).toBeNull();
        expect(text('.others-title')).toBe(RESULTS_TEXTS.othersDecided);
        expect(text('[data-testid="nope-toggle"]')).toContain('Pas miam · 2');
      });

      it('goes back to the suggestion with "Changer d’avis"', async () => {
        await click(q('[data-testid="change-mind"]'));

        expect(text('h1')).toBe('Vos 3 coups de cœur ce soir');
        expect(q('[data-testid="go-top"]')).not.toBeNull();
      });
    });

    it('makes another restaurant the choice with the "On y va" of its row, the first one stays a "Miam"', async () => {
      await click(qa('.other-go')[0]);

      expect(text('h1')).toBe(RESULTS_TEXTS.decided(third.name));
      expect(q('[data-testid="top-card"] .card-name')?.textContent?.trim()).toBe(third.name);
      expect(qa('.other .other-name').map((el) => el.textContent?.trim())).toEqual([
        first.name,
        fourth.name,
      ]);
    });
  });

  describe('decided state (one restaurant picked)', () => {
    it('shows the picked restaurant even when it is not the first "Miam"', async () => {
      await open([
        [first.id, 'liked'],
        [second.id, 'picked'],
      ]);

      expect(text('h1')).toBe(RESULTS_TEXTS.decided(second.name));
      expect(q('[data-testid="top-card"] .card-name')?.textContent?.trim()).toBe(second.name);
      expect(qa('.other .other-name').map((el) => el.textContent?.trim())).toEqual([first.name]);
    });
  });

  describe('hearts burst', () => {
    beforeEach(() => vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }));
    afterEach(() => vi.useRealTimers());

    it('shows the burst when a restaurant is chosen and removes it after 1.5 seconds', async () => {
      await open([[first.id, 'liked']]);
      expect(q('[data-testid="burst"]')).toBeNull();

      await click(q('[data-testid="go-top"]'));
      expect(q('[data-testid="burst"]')).not.toBeNull();
      expect(qa('[data-testid="burst"] svg').length).toBe(12);

      vi.advanceTimersByTime(1499);
      await fixture.whenStable();
      expect(q('[data-testid="burst"]')).not.toBeNull();

      vi.advanceTimersByTime(1);
      await fixture.whenStable();
      expect(q('[data-testid="burst"]')).toBeNull();
    });

    it('stops the timer when the screen is destroyed', async () => {
      await open([[first.id, 'liked']]);
      await click(q('[data-testid="go-top"]'));

      fixture.destroy();

      expect(vi.getTimerCount()).toBe(0);
    });
  });

  describe('empty state (no "Miam")', () => {
    beforeEach(() =>
      open([
        [first.id, 'disliked'],
        [second.id, 'disliked'],
      ]),
    );

    it('shows the empty title, the encouraging card and a single way to retry', () => {
      expect(text('h1')).toBe(RESULTS_TEXTS.emptyTitle);
      expect(text('.subtitle')).toBe(RESULTS_TEXTS.emptySubtitle);
      expect(q('[data-testid="top-card"]')).toBeNull();
      expect(text('[data-testid="empty-card"]')).toContain(RESULTS_TEXTS.emptyCardTitle);
      expect(q('[data-testid="empty-card"] [data-testid="deck-link"]')?.getAttribute('href')).toBe(
        '/deck',
      );
      expect(root.textContent).not.toContain('Voir à proximité');
    });

    it('opens the "Pas miam" list at first', () => {
      expect(q('[data-testid="nope-toggle"]')?.getAttribute('aria-expanded')).toBe('true');
      expect(qa('.nope-row').length).toBe(2);
    });

    it('goes back to the suggestion when one restaurant is revived', async () => {
      await click(qa('.revive')[0]);

      expect(text('h1')).toBe('Vos 1 coup de cœur ce soir');
      expect(q('[data-testid="top-card"] .card-name')?.textContent?.trim()).toBe(first.name);
    });
  });
});
