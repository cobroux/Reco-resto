import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { EnrichedPhoto } from '../../enrichment/card-enrichment';
import { Restaurant } from '../../restaurant/restaurant';
import { DECK_TEXTS } from '../deck.texts';
import { RoutePreviewService } from '../route-preview.service';
import { StubRoutePreviewService } from '../stub/stub-route-preview.service';
import { SwipeDirection } from './swipe-gesture';
import { SWIPE_ANIMATION_MS, SwipeCardComponent, animationDuration } from './swipe-card.component';

const FRIDAY_EVENING = '2026-11-27T20:00:00+01:00';
const OPEN_FRIDAY = [[], [], [], [], [], [{ open: '19:00', close: '23:00' }], []];

const FULL: Restaurant = {
  id: 'r1',
  name: 'Baan Thaï',
  distanceMeters: 1100,
  cuisine: 'Thaïlandaise',
  keywords: ['Options végé', 'Épicé'],
  priceLevel: '€€',
  weeklyHours: OPEN_FRIDAY,
  photoUrl: 'stub/restaurant.svg',
  dish: { name: 'Curry vert au tofu' },
};

const BARE: Restaurant = {
  id: 'r2',
  name: 'Le Petit Potager',
  distanceMeters: 600,
  cuisine: 'Française',
  keywords: [],
  weeklyHours: OPEN_FRIDAY,
};

/** jsdom may lack PointerEvent; Angular listens by event type, so a MouseEvent of that type works. */
function pointer(type: string, clientX: number, clientY: number): Event {
  return pointerWith(type, clientX, clientY, {});
}

function pointerWith(type: string, clientX: number, clientY: number, init: MouseEventInit): Event {
  const Ctor = (globalThis as { PointerEvent?: typeof MouseEvent }).PointerEvent ?? MouseEvent;
  const buttons = type === 'pointermove' ? 1 : 0;
  // Browsers mark the mouse and the first finger as primary; jsdom defaults to false.
  const real = { clientX, clientY, bubbles: true, buttons, isPrimary: true, ...init };
  return new Ctor(type, real as MouseEventInit);
}

describe('SwipeCardComponent', () => {
  let fixture: ComponentFixture<SwipeCardComponent>;
  let root: HTMLElement;
  let swipes: SwipeDirection[];

  async function create(restaurant: Restaurant): Promise<void> {
    fixture = TestBed.createComponent(SwipeCardComponent);
    fixture.componentRef.setInput('restaurant', restaurant);
    fixture.componentRef.setInput('targetDateTime', FRIDAY_EVENING);
    swipes = [];
    fixture.componentInstance.swiped.subscribe((d) => swipes.push(d));
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const text = () => root.textContent ?? '';
  const card = () => root.querySelector('article') as HTMLElement;

  async function drag(dx: number, dy: number): Promise<void> {
    card().dispatchEvent(pointer('pointerdown', 100, 100));
    card().dispatchEvent(pointer('pointermove', 100 + dx, 100 + dy));
    await fixture.whenStable();
    card().dispatchEvent(pointer('pointerup', 100 + dx, 100 + dy));
    await fixture.whenStable();
  }

  describe('content (US1 AS2)', () => {
    beforeEach(() => create(FULL));

    it('shows the photo', () => {
      expect(root.querySelector('img.photo')?.getAttribute('src')).toBe('stub/restaurant.svg');
      expect(root.querySelector('[data-testid="tablecloth"]')).toBeNull();
    });

    it('shows distance, name, cuisine, keywords and price level', () => {
      expect(text()).toContain('1,1 km');
      expect(root.querySelector('h2')?.textContent).toContain('Baan Thaï');
      const chips = [...root.querySelectorAll('.chip')].map((c) => c.textContent?.trim());
      expect(chips).toEqual(['Thaïlandaise', 'Options végé', 'Épicé', '€€']);
    });

    it('shows the opening status of the targeted moment', () => {
      expect(text()).toContain(DECK_TEXTS.openUntil('23:00'));
      expect(root.querySelector('.open .tile-label')?.textContent).toBe(DECK_TEXTS.open);
      expect(root.querySelector('.open .open-time')?.textContent).toBe(DECK_TEXTS.until('23:00'));
    });

    it('names a dish to taste', () => {
      expect(text()).toContain(DECK_TEXTS.toTaste);
      expect(text()).toContain('Curry vert au tofu');
      expect(root.querySelector('[data-testid="default-dish"]')).toBeNull();
    });
  });

  describe('fallbacks', () => {
    beforeEach(() => create(BARE));

    it('shows the tablecloth visual when there is no photo (AS3)', () => {
      expect(root.querySelector('img.photo')).toBeNull();
      expect(root.querySelector('[data-testid="tablecloth"]')).not.toBeNull();
    });

    it('shows the address under the name when it is known', async () => {
      await create({ ...BARE, address: '3 rue X, 75002 Paris' });

      const address = root.querySelector('[data-testid="address"]');
      expect(address?.textContent?.trim()).toBe('3 rue X, 75002 Paris');
      const name = root.querySelector('h2') as HTMLElement;
      expect(name.nextElementSibling).toBe(address);
    });

    it('shows the reason of the model with a sparkle when there is one', async () => {
      await create({ ...BARE, reason: 'Burger maison réputé' });

      expect(root.querySelector('[data-testid="reason"]')?.textContent?.trim()).toBe('✨ Burger maison réputé');
    });

    it('shows no reason line without a reason', async () => {
      await create(BARE);

      expect(root.querySelector('[data-testid="reason"]')).toBeNull();
    });

    it('shows no address line when the address is not known', async () => {
      await create(BARE);

      expect(root.querySelector('[data-testid="address"]')).toBeNull();
    });

    it('shows the default dish visual and no dish name when no dish is known (AS4)', () => {
      expect(root.querySelector('[data-testid="default-dish"]')).not.toBeNull();
      expect(root.querySelector('.dish-name')).toBeNull();
    });

    it('hides the price chip when the price level is unknown', () => {
      const chips = [...root.querySelectorAll('.chip')].map((c) => c.textContent?.trim());
      expect(chips).toEqual(['Française']);
    });
  });

  describe('Google Places data (spec 008, T21-T24, T30)', () => {
    it('shows the photo when the restaurant has a photo address (T21)', async () => {
      await create({ ...FULL, photoUrl: '/api/photos/place1/photo1' });

      expect(root.querySelector('img.photo')?.getAttribute('src')).toBe(
        '/api/photos/place1/photo1',
      );
      expect(root.querySelector('[data-testid="tablecloth"]')).toBeNull();
    });

    it('falls back to the tablecloth when the photo cannot be loaded (FR-010, T30)', async () => {
      await create({ ...FULL, photoUrl: '/api/photos/place1/broken' });

      root.querySelector('img.photo')?.dispatchEvent(new Event('error'));
      await fixture.whenStable();

      expect(root.querySelector('img.photo')).toBeNull();
      expect(root.querySelector('[data-testid="tablecloth"]')).not.toBeNull();
    });

    it('shows the photo of the next restaurant after a failed one', async () => {
      await create({ ...FULL, photoUrl: '/api/photos/place1/broken' });
      root.querySelector('img.photo')?.dispatchEvent(new Event('error'));
      await fixture.whenStable();

      fixture.componentRef.setInput('restaurant', {
        ...FULL,
        id: 'r3',
        photoUrl: '/api/photos/place2/ok',
      });
      await fixture.whenStable();

      expect(root.querySelector('img.photo')?.getAttribute('src')).toBe('/api/photos/place2/ok');
    });

    it('shows the tablecloth when there is no photo (T22)', async () => {
      await create(BARE);

      expect(root.querySelector('img.photo')).toBeNull();
      expect(root.querySelector('[data-testid="tablecloth"]')).not.toBeNull();
    });

    it('shows no opening status without opening hours (T23)', async () => {
      await create({ ...BARE, weeklyHours: [[], [], [], [], [], [], []] });

      expect(text()).not.toContain(DECK_TEXTS.openUntil(''));
    });

    it('shows no price chip without price level (T24)', async () => {
      await create(BARE);

      expect(root.querySelector('.chip.price')).toBeNull();
    });
  });

  describe('Google Maps attribution (spec 008, FR-014, T40, T41, SC-007)', () => {
    const attribution = () => root.querySelector('.hero [data-testid="google-attribution"]');

    it('shows the untranslated "Google Maps" text on the photo area', async () => {
      await create(FULL);

      const label = root.querySelector('.hero [data-testid="google-maps"]');
      expect(label?.textContent?.trim()).toBe(DECK_TEXTS.googleMaps);
      expect(DECK_TEXTS.googleMaps).toBe('Google Maps');
      expect(label?.getAttribute('translate')).toBe('no');
    });

    it('shows it on a card with the tablecloth too', async () => {
      await create(BARE);

      expect(root.querySelector('[data-testid="tablecloth"]')).not.toBeNull();
      expect(attribution()?.textContent).toContain('Google Maps');
    });

    it('shows the photo author(s) under it, linked to their page (T41)', async () => {
      await create({
        ...FULL,
        photoAttributions: [
          { displayName: 'Jane Doe', uri: 'https://maps.google.com/contrib/1' },
          { displayName: 'John Roe' },
        ],
      });

      const authors = root.querySelector('[data-testid="photo-authors"]') as HTMLElement;
      expect(authors).not.toBeNull();
      expect(
        root.querySelector('[data-testid="google-maps"]')?.compareDocumentPosition(authors),
      ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
      const link = authors.querySelector('a') as HTMLAnchorElement;
      expect(link.textContent?.trim()).toBe('Jane Doe');
      expect(link.getAttribute('href')).toBe('https://maps.google.com/contrib/1');
      expect(authors.textContent).toContain('John Roe');
    });

    it('lets an attribution link be clicked: a press on it starts no drag', async () => {
      await create({
        ...FULL,
        photoAttributions: [{ displayName: 'Jane Doe', uri: 'https://maps.google.com/contrib/1' }],
      });
      const link = root.querySelector('[data-testid="photo-authors"] a') as HTMLElement;

      link.dispatchEvent(pointer('pointerdown', 100, 100));
      card().dispatchEvent(pointer('pointermove', 250, 100));
      card().dispatchEvent(pointer('pointerup', 250, 100));
      await fixture.whenStable();

      expect(swipes).toEqual([]);
      expect(card().style.transform).toBe('');
    });

    it('shows no author line without photo attribution', async () => {
      await create(FULL);

      expect(root.querySelector('[data-testid="photo-authors"]')).toBeNull();
    });

    it('shows the data providers with "Google Maps"', async () => {
      await create({
        ...FULL,
        attributions: [{ provider: 'Some Provider', providerUri: 'https://provider.example' }],
      });

      const providers = attribution()?.querySelector('[data-testid="data-providers"]');
      expect(providers?.textContent).toContain('Some Provider');
      expect(providers?.querySelector('a')?.getAttribute('href')).toBe('https://provider.example');
    });
  });

  describe('website photo (spec 007, T5, T8, T9, T10, T21, PO-12)', () => {
    const ENRICHED = { url: 'https://cdn.example/salle.jpg' };

    async function setEnrichment(enrichment: EnrichedPhoto | undefined): Promise<void> {
      fixture.componentRef.setInput('enrichment', enrichment);
      await fixture.whenStable();
    }

    it('shows the enrichment photo without referrer when the restaurant has no photo (T5, PO-12)', async () => {
      await create(BARE);
      await setEnrichment(ENRICHED);

      const img = root.querySelector('img.photo');
      expect(img?.getAttribute('src')).toBe(ENRICHED.url);
      expect(img?.getAttribute('referrerpolicy')).toBe('no-referrer');
      expect(root.querySelector('[data-testid="tablecloth"]')).toBeNull();
    });

    it('keeps the data-source photo over the enrichment photo (T4)', async () => {
      await create(FULL);
      await setEnrichment(ENRICHED);

      expect(root.querySelector('img.photo')?.getAttribute('src')).toBe('stub/restaurant.svg');
    });

    it('shows the tablecloth without enrichment photo (T10)', async () => {
      await create(BARE);

      expect(root.querySelector('img.photo')).toBeNull();
      expect(root.querySelector('[data-testid="tablecloth"]')).not.toBeNull();
    });

    it('falls back to the tablecloth when the enrichment photo cannot be loaded (T9, 008 T063)', async () => {
      await create(BARE);
      await setEnrichment(ENRICHED);

      root.querySelector('img.photo')?.dispatchEvent(new Event('error'));
      await fixture.whenStable();

      expect(root.querySelector('img.photo')).toBeNull();
      expect(root.querySelector('[data-testid="tablecloth"]')).not.toBeNull();
    });

    it('keeps the drag going when the photo arrives during it (T21)', async () => {
      await create(BARE);
      card().dispatchEvent(pointer('pointerdown', 100, 100));
      card().dispatchEvent(pointer('pointermove', 160, 100));
      await fixture.whenStable();

      await setEnrichment(ENRICHED);

      expect(card().style.transform).toContain('translate(60px');
      expect(root.querySelector('img.photo')?.getAttribute('src')).toBe(ENRICHED.url);
      card().dispatchEvent(pointer('pointermove', 250, 100));
      card().dispatchEvent(pointer('pointerup', 250, 100));
      await fixture.whenStable();
      expect(swipes).toEqual(['right']);
    });
  });

  describe('website link (spec 007, US2, FR-007, FR-008, T13, T14, T16, PO-14)', () => {
    const WEBSITE = 'https://baan-thai.example/';
    const link = () => root.querySelector('.hero a.website') as HTMLAnchorElement | null;

    it('shows a link icon to the website at the top-left of the photo area, opening a new tab (T13)', async () => {
      await create({ ...FULL, website: WEBSITE });

      expect(link()).not.toBeNull();
      expect(link()!.getAttribute('href')).toBe(WEBSITE);
      expect(link()!.getAttribute('target')).toBe('_blank');
      expect(link()!.getAttribute('rel')).toBe('noopener noreferrer');
      expect(link()!.getAttribute('draggable')).toBe('false');
      expect(link()!.getAttribute('aria-label')).toBe(DECK_TEXTS.website);
      expect(DECK_TEXTS.website).toBe('Site du restaurant');
      expect(link()!.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
      expect(link()!.closest('[data-testid="google-attribution"]')).toBeNull();
    });

    it('shows no link without website (T14)', async () => {
      await create(FULL);

      expect(link()).toBeNull();
    });

    it('starts no drag and no swipe from a press on the link (T16)', async () => {
      await create({ ...FULL, website: WEBSITE });
      const capture = vi.fn();
      card().setPointerCapture = capture;

      link()!.dispatchEvent(pointer('pointerdown', 100, 100));
      card().dispatchEvent(pointer('pointermove', 250, 100));
      card().dispatchEvent(pointer('pointerup', 250, 100));
      await fixture.whenStable();

      expect(capture).not.toHaveBeenCalled();
      expect(card().style.transform).toBe('');
      expect(swipes).toEqual([]);
    });

    it('emits no reaction on a click (FR-008)', async () => {
      await create({ ...FULL, website: WEBSITE });
      link()!.addEventListener('click', (event) => event.preventDefault());

      link()!.click();
      await fixture.whenStable();

      expect(swipes).toEqual([]);
    });
  });

  describe('Unsplash credit (spec 007, FR-015, T40, T41)', () => {
    const CREDIT = {
      photographerName: 'Jane Doe',
      photographerUrl: 'https://unsplash.com/@janedoe?utm_source=reco_resto&utm_medium=referral',
      unsplashUrl: 'https://unsplash.com/?utm_source=reco_resto&utm_medium=referral',
    };
    const credit = () =>
      root.querySelector('.hero [data-testid="unsplash-credit"]') as HTMLElement | null;

    async function createWith(
      enrichment: EnrichedPhoto,
      restaurant: Restaurant = BARE,
    ): Promise<void> {
      await create(restaurant);
      fixture.componentRef.setInput('enrichment', enrichment);
      await fixture.whenStable();
    }

    it('shows "Photo : <photographer> / Unsplash" with both links in a new tab (T40)', async () => {
      await createWith({ url: 'https://images.unsplash.com/photo-1', credit: CREDIT });

      expect(credit()?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
        `${DECK_TEXTS.photoBy} Jane Doe / ${DECK_TEXTS.unsplash}`,
      );
      expect(DECK_TEXTS.photoBy).toBe('Photo :');
      const [photographer, unsplash] = [...credit()!.querySelectorAll('a')];
      expect(photographer.textContent?.trim()).toBe('Jane Doe');
      expect(photographer.getAttribute('href')).toBe(CREDIT.photographerUrl);
      expect(unsplash.textContent?.trim()).toBe('Unsplash');
      expect(unsplash.getAttribute('href')).toBe(CREDIT.unsplashUrl);
      for (const a of [photographer, unsplash]) {
        expect(a.getAttribute('target')).toBe('_blank');
        expect(a.getAttribute('rel')).toBe('noopener noreferrer');
        expect(a.getAttribute('draggable')).toBe('false');
      }
    });

    it('keeps the credit apart from the Google attribution and the website link', async () => {
      await createWith(
        { url: 'https://images.unsplash.com/photo-1', credit: CREDIT },
        { ...BARE, website: 'https://x.example/' },
      );

      expect(credit()!.closest('[data-testid="google-attribution"]')).toBeNull();
      expect(credit()!.contains(root.querySelector('a.website'))).toBe(false);
    });

    it('starts no drag nor reaction from a press or a click on a credit link (T40)', async () => {
      await createWith({ url: 'https://images.unsplash.com/photo-1', credit: CREDIT });
      for (const a of credit()!.querySelectorAll('a')) {
        a.addEventListener('click', (event) => event.preventDefault());
        a.dispatchEvent(pointer('pointerdown', 100, 100));
        card().dispatchEvent(pointer('pointermove', 250, 100));
        card().dispatchEvent(pointer('pointerup', 250, 100));
        a.click();
      }
      await fixture.whenStable();

      expect(card().style.transform).toBe('');
      expect(swipes).toEqual([]);
    });

    it('shows no credit for a website photo (T41)', async () => {
      await createWith({ url: 'https://cdn.example/salle.jpg' });

      expect(credit()).toBeNull();
    });

    it('shows no credit when the data-source photo is shown, nor when the photo failed', async () => {
      await createWith({ url: 'https://images.unsplash.com/photo-1', credit: CREDIT }, FULL);
      expect(credit()).toBeNull();

      await createWith({ url: 'https://images.unsplash.com/photo-1', credit: CREDIT });
      root.querySelector('img.photo')?.dispatchEvent(new Event('error'));
      await fixture.whenStable();
      expect(credit()).toBeNull();
    });
  });

  describe('gestures (US2 AS4, FR-005)', () => {
    beforeEach(() => create(FULL));

    it.each([
      [-150, 0, 'left'],
      [150, 0, 'right'],
      [0, -150, 'up'],
    ] as const)('a drag of (%i, %i) emits %s', async (dx, dy, expected) => {
      await drag(dx, dy);

      expect(swipes).toEqual([expected]);
    });

    it('brings the card back without reaction under the threshold', async () => {
      await drag(60, 0);

      expect(swipes).toEqual([]);
      expect(card().style.transform).toBe('');
    });

    it('moves the card and shows the "Miam" stamp while dragging right', async () => {
      card().dispatchEvent(pointer('pointerdown', 100, 100));
      card().dispatchEvent(pointer('pointermove', 210, 100));
      await fixture.whenStable();

      expect(card().style.transform).toContain('translate(110px');
      expect((root.querySelector('.stamp.like') as HTMLElement).style.opacity).toBe('1');
      expect((root.querySelector('.stamp.nope') as HTMLElement).style.opacity).toBe('0');
    });

    it('ignores a drag started with another mouse button than the main one', async () => {
      card().dispatchEvent(pointerWith('pointerdown', 100, 100, { button: 2 }));
      card().dispatchEvent(pointerWith('pointermove', 250, 100, { buttons: 2 }));
      card().dispatchEvent(pointer('pointerup', 250, 100));
      await fixture.whenStable();

      expect(swipes).toEqual([]);
    });

    it('drops the drag when the button was released outside the card (lost pointer)', async () => {
      card().dispatchEvent(pointer('pointerdown', 100, 100));
      card().dispatchEvent(pointerWith('pointermove', 250, 100, { buttons: 0 }));
      await fixture.whenStable();

      expect(card().style.transform).toBe('');
      card().dispatchEvent(pointer('pointerup', 250, 100));
      await fixture.whenStable();
      expect(swipes).toEqual([]);
    });

    it('ignores gestures when disabled', async () => {
      fixture.componentRef.setInput('disabled', true);
      await fixture.whenStable();
      await drag(150, 0);

      expect(swipes).toEqual([]);
    });
  });

  describe('back of the card (spec 004)', () => {
    const WITH_DETAILS: Restaurant = {
      ...FULL,
      address: '3 rue X, 75002 Paris',
      menu: [
        { name: 'Curry vert', description: 'Lait de coco', price: '14 €' },
        { name: 'Riz parfumé' },
      ],
    };
    const flipButton = () => root.querySelector('button.flip-btn') as HTMLButtonElement;
    const back = () => root.querySelector('[data-testid="back"]');

    async function flip(): Promise<void> {
      flipButton().click();
      await fixture.whenStable();
    }

    async function tap(): Promise<void> {
      card().dispatchEvent(pointer('pointerdown', 100, 100));
      card().dispatchEvent(pointer('pointerup', 100, 100));
      await fixture.whenStable();
    }

    describe('flip (FR-001)', () => {
      beforeEach(() => create(WITH_DETAILS));

      it('shows the front first', () => {
        expect(back()).toBeNull();
        expect(root.querySelector('img.photo')).not.toBeNull();
      });

      it('shows the back with the "↻" button, and the front again with the same button', async () => {
        await flip();
        expect(back()).not.toBeNull();
        expect(root.querySelector('img.photo')).toBeNull();

        await flip();
        expect(back()).toBeNull();
        expect(root.querySelector('img.photo')).not.toBeNull();
      });

      it('flips in both directions with a tap on the card', async () => {
        await tap();
        expect(back()).not.toBeNull();

        await tap();
        expect(back()).toBeNull();
      });

      it('does not flip after a drag, even a short one that stays under the swipe threshold', async () => {
        await drag(60, 0);

        expect(back()).toBeNull();
      });

      it('does not flip and emits no reaction when the flip button is pressed', async () => {
        flipButton().dispatchEvent(pointer('pointerdown', 100, 100));
        flipButton().dispatchEvent(pointer('pointerup', 100, 100));
        await fixture.whenStable();

        expect(swipes).toEqual([]);
      });

      it('does not flip when disabled', async () => {
        fixture.componentRef.setInput('disabled', true);
        await fixture.whenStable();
        await tap();

        expect(back()).toBeNull();
      });
    });

    describe('content (FR-002, FR-004)', () => {
      beforeEach(async () => {
        await create(WITH_DETAILS);
        await flip();
      });

      it('shows name, cuisine, distance, price level and keywords', () => {
        const head = root.querySelector('[data-testid="back"]')?.textContent ?? '';

        expect(head).toContain('Baan Thaï');
        expect(head).toContain('Thaïlandaise');
        expect(head).toContain('1,1 km');
        expect(head).toContain('€€');
        expect(head).toContain('Options végé');
        expect(head).toContain('Épicé');
      });

      it('lists the week with the targeted day highlighted', () => {
        const rows = [...root.querySelectorAll('[data-testid="hours"] .hours-row')];

        expect(rows).toHaveLength(7);
        expect(root.querySelector('[data-testid="hours"]')?.textContent).toContain(
          DECK_TEXTS.hours,
        );
        const targeted = rows.filter((row) => row.classList.contains('targeted'));
        expect(targeted).toHaveLength(1);
        expect(targeted[0].textContent).toContain('Vendredi');
        expect(targeted[0].textContent).toContain('19:00 – 23:00');
      });

      it('shows the address and the "Itinéraire" button', () => {
        const address = root.querySelector('[data-testid="back-address"]');

        expect(address?.textContent).toContain(DECK_TEXTS.addressTitle);
        expect(address?.textContent).toContain('3 rue X, 75002 Paris');
        expect(address?.querySelector('button.route-btn')?.textContent).toContain(DECK_TEXTS.route);
      });

      it('lists the dishes with their description and price', () => {
        const menu = root.querySelector('[data-testid="menu"]');
        const dishes = [...(menu?.querySelectorAll('.dish') ?? [])];

        expect(menu?.textContent).toContain(DECK_TEXTS.menu);
        expect(dishes).toHaveLength(2);
        expect(dishes[0].textContent).toContain('Curry vert');
        expect(dishes[0].textContent).toContain('Lait de coco');
        expect(dishes[0].textContent).toContain('14 €');
        expect(dishes[1].textContent).toContain('Riz parfumé');
      });
    });

    describe('missing data (FR-003, SC-001)', () => {
      it('shows no "Menu" section without a menu, nor with an empty one', async () => {
        await create({ ...WITH_DETAILS, menu: undefined });
        await flip();
        expect(root.querySelector('[data-testid="menu"]')).toBeNull();

        await create({ ...WITH_DETAILS, menu: [] });
        await flip();
        expect(root.querySelector('[data-testid="menu"]')).toBeNull();
      });

      it('shows no "Horaires" section without opening hours', async () => {
        await create({ ...WITH_DETAILS, weeklyHours: [[], [], [], [], [], [], []] });
        await flip();

        expect(root.querySelector('[data-testid="hours"]')).toBeNull();
      });
    });

    describe('route preview (US2)', () => {
      const previewEl = () => root.querySelector('[data-testid="route-preview"]');
      const routeButton = () => root.querySelector('button.route-btn') as HTMLButtonElement;

      beforeEach(async () => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: RoutePreviewService, useClass: StubRoutePreviewService },
          ],
        });
        await create(WITH_DETAILS);
        await flip();
      });

      it('shows no preview until "Itinéraire" is pressed', () => {
        expect(previewEl()).toBeNull();
      });

      it('opens the preview inside the card, with the travel times (AS1)', async () => {
        routeButton().click();
        await fixture.whenStable();

        expect(previewEl()).not.toBeNull();
        expect(previewEl()?.textContent).toContain(DECK_TEXTS.travelModes.walk);
        expect(root.querySelector('[data-testid="back"]')?.contains(previewEl())).toBe(true);
      });

      it('is not a reaction: no swipe is emitted and the card stays (FR-006, AS2)', async () => {
        const lefts: SwipeDirection[] = [];
        fixture.componentInstance.left.subscribe((d) => lefts.push(d));
        routeButton().dispatchEvent(pointer('pointerdown', 100, 100));
        routeButton().click();
        routeButton().dispatchEvent(pointer('pointerup', 100, 100));
        await fixture.whenStable();

        expect(swipes).toEqual([]);
        expect(lefts).toEqual([]);
        expect(back()).not.toBeNull();
      });

      it('closes the preview when the card is flipped back', async () => {
        routeButton().click();
        await fixture.whenStable();
        await flip();
        await flip();

        expect(previewEl()).toBeNull();
      });
    });

    describe('gestures on the back (plan D-04-3)', () => {
      beforeEach(async () => {
        await create(WITH_DETAILS);
        await flip();
      });

      it('does not swipe: scrolling inside the card must work', async () => {
        await drag(150, 0);
        await drag(0, -150);

        expect(swipes).toEqual([]);
        expect(back()).not.toBeNull();
      });

      it('still treats a move under the 6 px drag threshold as a tap (plan D-04-4)', async () => {
        await drag(5, 0);

        expect(back()).toBeNull();
      });
    });
  });

  describe('leave animation (D18)', () => {
    let lefts: SwipeDirection[];
    const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

    beforeEach(async () => {
      TestBed.configureTestingModule({
        providers: [{ provide: SWIPE_ANIMATION_MS, useValue: 20 }],
      });
      await create(FULL);
      lefts = [];
      fixture.componentInstance.left.subscribe((d) => lefts.push(d));
    });

    it('emits the reaction at once, flies off in its direction, then emits "left"', async () => {
      await drag(150, 0);

      expect(swipes).toEqual(['right']);
      expect(lefts).toEqual([]);
      expect(card().classList).toContain('leaving');
      expect(card().style.transform).toContain('translate(120vw');

      await sleep(40);
      expect(lefts).toEqual(['right']);
    });

    it('animates a button reaction with leave()', async () => {
      fixture.componentInstance.leave('up');
      await fixture.whenStable();

      expect(swipes).toEqual(['up']);
      expect(card().style.transform).toContain('-120vh');
      await sleep(40);
      expect(lefts).toEqual(['up']);
    });

    it('flies left for a dislike', async () => {
      fixture.componentInstance.leave('left');
      await fixture.whenStable();

      expect(card().style.transform).toContain('translate(-120vw');
    });

    it('ignores gestures and a second leave while leaving', async () => {
      await drag(150, 0);
      fixture.componentInstance.leave('left');
      await drag(-150, 0);
      await sleep(40);

      expect(swipes).toEqual(['right']);
      expect(lefts).toEqual(['right']);
    });
  });

  describe('animation duration', () => {
    it('is 300 ms, or 0 when the user prefers reduced motion', () => {
      const media = (reduce: boolean) => ({ matchMedia: () => ({ matches: reduce }) }) as never;

      expect(animationDuration(media(false))).toBe(300);
      expect(animationDuration(media(true))).toBe(0);
      expect(animationDuration(undefined)).toBe(300);
    });
  });
});
