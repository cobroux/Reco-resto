import { DeckInput } from '../deck/deck.types';
import { toDeckInput } from './deck-input';
import { Restaurant, SearchResult } from './search-result';

function resultOf(moment: string | undefined, ...deck: Restaurant[]): SearchResult {
  return {
    criteria: { cuisines: ['italian'], diets: [], places: ['Opéra, Paris'], moment },
    deck,
  };
}

const THAI: Restaurant = {
  name: 'Baan Thaï',
  address: '3 rue X, Paris',
  distanceMeters: 1100,
  cuisine: 'thai',
  diets: ['vegetarian'],
  keywords: ['spicy'],
  priceLevel: '€€',
  photoUrl: 'http://photo',
  signatureDish: 'Curry vert',
  openingHours: {
    MONDAY: [{ opens: '12:00', closes: '14:30' }],
    SUNDAY: [
      { opens: '12:00', closes: '14:30' },
      { opens: '19:00', closes: '23:00' },
    ],
  },
};

describe('toDeckInput (spec 002 result for the swipe deck of spec 003)', () => {
  const NOON_UTC_NOV = new Date('2026-11-25T10:00:00Z');

  it('has no moment label: the backend returns the moment normalized, not the words of the user', () => {
    expect(toDeckInput(resultOf('now', THAI), NOON_UTC_NOV).momentLabel).toBeNull();
  });

  describe('targeted date-time (local time of Paris)', () => {
    it('is the current Paris time for "now", with the winter offset', () => {
      expect(toDeckInput(resultOf('now', THAI), NOON_UTC_NOV).targetDateTime).toBe(
        '2026-11-25T11:00:00+01:00',
      );
    });

    it('is the current Paris time for "now", with the summer offset', () => {
      expect(
        toDeckInput(resultOf('now', THAI), new Date('2026-07-01T10:00:00Z')).targetDateTime,
      ).toBe('2026-07-01T12:00:00+02:00');
    });

    it('is the current time when the result has no moment', () => {
      expect(toDeckInput(resultOf(undefined, THAI), NOON_UTC_NOV).targetDateTime).toBe(
        '2026-11-25T11:00:00+01:00',
      );
    });

    it('keeps an explicit moment as written, with the Paris offset of that day', () => {
      expect(toDeckInput(resultOf('2026-11-28T12:00', THAI), NOON_UTC_NOV).targetDateTime).toBe(
        '2026-11-28T12:00:00+01:00',
      );
      expect(toDeckInput(resultOf('2026-07-14T20:30', THAI), NOON_UTC_NOV).targetDateTime).toBe(
        '2026-07-14T20:30:00+02:00',
      );
    });
  });

  describe('restaurants', () => {
    function first(restaurant: Restaurant): DeckInput['restaurants'][number] {
      return toDeckInput(resultOf('now', restaurant), NOON_UTC_NOV).restaurants[0];
    }

    it('maps a restaurant of the backend to a restaurant of the deck', () => {
      const mapped = first(THAI);

      expect(mapped.id).toBe('Baan Thaï|3 rue X, Paris');
      expect(mapped.name).toBe('Baan Thaï');
      expect(mapped.address).toBe('3 rue X, Paris');
      expect(mapped.distanceMeters).toBe(1100);
      expect(mapped.cuisine).toBe('thai');
      expect(mapped.keywords).toEqual(['spicy', 'vegetarian']);
      expect(mapped.priceLevel).toBe('€€');
      expect(mapped.photoUrl).toBe('http://photo');
      expect(mapped.dish).toEqual({ name: 'Curry vert' });
    });

    it('carries the menu and the phone of the backend (spec 004)', () => {
      const mapped = first({
        ...THAI,
        phone: '+33 1 23 45 67 89',
        menu: [
          { name: 'Curry vert', description: 'Lait de coco', price: '14 €' },
          { name: 'Riz' },
        ],
      });

      expect(mapped.phone).toBe('+33 1 23 45 67 89');
      expect(mapped.menu).toEqual([
        { name: 'Curry vert', description: 'Lait de coco', price: '14 €' },
        { name: 'Riz' },
      ]);
    });

    it('turns the opening hours into seven days indexed Sunday = 0', () => {
      const hours = first(THAI).weeklyHours;

      expect(hours).toHaveLength(7);
      expect(hours[0]).toEqual([
        { open: '12:00', close: '14:30' },
        { open: '19:00', close: '23:00' },
      ]);
      expect(hours[1]).toEqual([{ open: '12:00', close: '14:30' }]);
      expect(hours.slice(2)).toEqual([[], [], [], [], []]);
    });

    it('leaves absent what the backend does not provide, without inventing it', () => {
      const mapped = first({ name: 'Bare', address: '1 rue A', distanceMeters: 50 });

      expect(mapped.cuisine).toBe('');
      expect(mapped.keywords).toEqual([]);
      expect(mapped.priceLevel).toBeUndefined();
      expect(mapped.photoUrl).toBeUndefined();
      expect(mapped.dish).toBeUndefined();
      expect(mapped.menu).toBeUndefined();
      expect(mapped.phone).toBeUndefined();
      expect(mapped.weeklyHours).toEqual([[], [], [], [], [], [], []]);
    });

    describe('Google Places fields (spec 008, T21, T31, T32)', () => {
      const GOOGLE: Restaurant = {
        ...THAI,
        website: 'https://baan-thai.example',
        placeId: 'ChIJ_baan-thai',
        photoAttributions: [{ displayName: 'Jane Doe', uri: 'https://maps.google.com/contrib/1' }],
        photoSourceUri: 'https://maps.google.com/photo/1',
        attributions: [{ provider: 'Some Provider', providerUri: 'https://provider.example' }],
      };

      it('maps a response with the new optional fields without error', () => {
        const mapped = first(GOOGLE);

        expect(mapped.name).toBe('Baan Thaï');
        expect(mapped.website).toBe('https://baan-thai.example');
        expect(mapped.photoAttributions).toEqual([
          { displayName: 'Jane Doe', uri: 'https://maps.google.com/contrib/1' },
        ]);
        expect(mapped.photoSourceUri).toBe('https://maps.google.com/photo/1');
        expect(mapped.attributions).toEqual([
          { provider: 'Some Provider', providerUri: 'https://provider.example' },
        ]);
      });

      it('uses the Google place id as deck id when present (research R11)', () => {
        expect(first(GOOGLE).id).toBe('ChIJ_baan-thai');
      });

      it('falls back to name|address as deck id without place id', () => {
        expect(first(THAI).id).toBe('Baan Thaï|3 rue X, Paris');
      });

      it('maps a response without the new fields as before', () => {
        const mapped = first(THAI);

        expect(mapped.website).toBeUndefined();
        expect(mapped.photoAttributions).toBeUndefined();
        expect(mapped.photoSourceUri).toBeUndefined();
        expect(mapped.attributions).toBeUndefined();
      });
    });

    it('keeps the order of the deck', () => {
      const input = toDeckInput(
        resultOf(
          'now',
          { ...THAI, name: 'A' },
          { ...THAI, name: 'B' },
          { ...THAI, name: 'C' },
        ),
        NOON_UTC_NOV,
      );

      expect(input.restaurants.map((restaurant) => restaurant.name)).toEqual(['A', 'B', 'C']);
    });

    it('leaves out a restaurant without distance or address: the deck needs both', () => {
      const input = toDeckInput(
        resultOf(
          'now',
          { name: 'No distance', address: '1 rue A' },
          { name: 'No address', distanceMeters: 10 },
          { name: 'Ok', address: '1 rue B', distanceMeters: 10 },
        ),
        NOON_UTC_NOV,
      );

      expect(input.restaurants.map((restaurant) => restaurant.name)).toEqual(['Ok']);
    });
  });
});
