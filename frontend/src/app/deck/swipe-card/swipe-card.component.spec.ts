import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Restaurant } from '../../restaurant/restaurant';
import { DECK_TEXTS } from '../deck.texts';
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
