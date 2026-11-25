import { OpeningRange, Restaurant } from '../../restaurant/restaurant';
import { DeckInput } from '../deck.types';

/**
 * Fixed data set used until feature 002 provides real search results (decision D6).
 * Fictional restaurants in Paris; the photo is a local stub illustration.
 */

const LUNCH: OpeningRange = { open: '12:00', close: '14:30' };
const DINNER: OpeningRange = { open: '19:00', close: '23:00' };
const LATE_DINNER: OpeningRange = { open: '19:00', close: '01:00' };
const ALL_DAY: OpeningRange = { open: '11:30', close: '22:30' };

/** Same ranges every day except Sunday (index 0), closed. */
function weekdays(...ranges: OpeningRange[]): OpeningRange[][] {
  return [[], ranges, ranges, ranges, ranges, ranges, ranges];
}

/** Same ranges every day. */
function everyDay(...ranges: OpeningRange[]): OpeningRange[][] {
  return [ranges, ranges, ranges, ranges, ranges, ranges, ranges];
}

const PHOTO = 'stub/restaurant.svg';

export const STUB_RESTAURANTS: readonly Restaurant[] = [
  {
    id: 'stub-01',
    name: 'Trattoria Verde',
    distanceMeters: 350,
    cuisine: 'Italienne',
    keywords: ['Options végé'],
    priceLevel: '€€',
    weeklyHours: weekdays(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Risotto aux champignons' },
  },
  {
    id: 'stub-02',
    name: 'Baan Thaï',
    distanceMeters: 1100,
    cuisine: 'Thaïlandaise',
    keywords: ['Options végé', 'Épicé'],
    priceLevel: '€€',
    weeklyHours: everyDay(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Curry vert au tofu' },
  },
  {
    id: 'stub-03',
    name: 'Le Petit Potager',
    distanceMeters: 600,
    cuisine: 'Française',
    keywords: ['100 % végétarien'],
    priceLevel: '€€',
    weeklyHours: weekdays(LUNCH, DINNER),
    dish: { name: 'Velouté de saison' },
  },
  {
    id: 'stub-04',
    name: 'Sakura Bento',
    distanceMeters: 1560,
    cuisine: 'Japonaise',
    keywords: ['Sans lactose'],
    priceLevel: '€',
    weeklyHours: everyDay(ALL_DAY),
    photoUrl: PHOTO,
    dish: { name: 'Ramen miso' },
  },
  {
    id: 'stub-05',
    name: "Jardin d'Olive",
    distanceMeters: 1400,
    cuisine: 'Libanaise',
    keywords: ['Vegan'],
    priceLevel: '€',
    weeklyHours: weekdays(LUNCH, DINNER),
    photoUrl: PHOTO,
  },
  {
    id: 'stub-06',
    name: 'Pho Montmartre',
    distanceMeters: 820,
    cuisine: 'Vietnamienne',
    keywords: ['Options végé'],
    priceLevel: '€',
    weeklyHours: everyDay(ALL_DAY),
    photoUrl: PHOTO,
    dish: { name: 'Phở au bœuf' },
  },
  {
    id: 'stub-07',
    name: 'La Bella Pasta',
    distanceMeters: 450,
    cuisine: 'Italienne',
    keywords: ['Fait maison'],
    priceLevel: '€€',
    weeklyHours: weekdays(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Tagliatelles au pesto' },
  },
  {
    id: 'stub-08',
    name: 'Le Comptoir du Marais',
    distanceMeters: 990,
    cuisine: 'Française',
    keywords: ['Terrasse'],
    priceLevel: '€€€',
    weeklyHours: weekdays(LUNCH, LATE_DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Confit de canard' },
  },
  {
    id: 'stub-09',
    name: 'Dragon de Belleville',
    distanceMeters: 2100,
    cuisine: 'Chinoise',
    keywords: ['Épicé'],
    priceLevel: '€',
    weeklyHours: everyDay(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Raviolis vapeur' },
  },
  {
    id: 'stub-10',
    name: 'Masala Opéra',
    distanceMeters: 700,
    cuisine: 'Indienne',
    keywords: ['Options végé', 'Épicé'],
    priceLevel: '€€',
    weeklyHours: everyDay(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Dal makhani' },
  },
  {
    id: 'stub-11',
    name: 'Seoul Kitchen',
    distanceMeters: 1250,
    cuisine: 'Coréenne',
    keywords: ['Épicé'],
    priceLevel: '€€',
    weeklyHours: weekdays(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Bibimbap' },
  },
  {
    id: 'stub-12',
    name: 'Taqueria Bastille',
    distanceMeters: 1800,
    cuisine: 'Mexicaine',
    keywords: ['Sans gluten'],
    priceLevel: '€',
    weeklyHours: weekdays(LATE_DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Tacos al pastor' },
  },
  {
    id: 'stub-13',
    name: 'Osteria Pigalle',
    distanceMeters: 950,
    cuisine: 'Italienne',
    keywords: ['Options végé'],
    priceLevel: '€€',
    weeklyHours: everyDay(DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Burrata et tomates' },
  },
  {
    id: 'stub-14',
    name: 'Le Bouillon Saint-Martin',
    distanceMeters: 1650,
    cuisine: 'Française',
    keywords: ['Petits prix'],
    priceLevel: '€',
    weeklyHours: everyDay(ALL_DAY),
    photoUrl: PHOTO,
    dish: { name: 'Œuf mayonnaise' },
  },
  {
    id: 'stub-15',
    name: 'Kyoto Ramen',
    distanceMeters: 520,
    cuisine: 'Japonaise',
    keywords: ['Options végé'],
    priceLevel: '€€',
    weeklyHours: weekdays(LUNCH, DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Ramen shoyu' },
  },
  {
    id: 'stub-16',
    name: 'Casa Tapas',
    distanceMeters: 2400,
    cuisine: 'Espagnole',
    keywords: ['À partager'],
    priceLevel: '€€',
    weeklyHours: weekdays(LATE_DINNER),
    photoUrl: PHOTO,
    dish: { name: 'Patatas bravas' },
  },
];

/** Friday 27 November 2026, 20:00 in Paris: "ce soir". */
export const STUB_DECK_INPUT: DeckInput = {
  momentLabel: 'ce soir',
  targetDateTime: '2026-11-27T20:00:00+01:00',
  restaurants: STUB_RESTAURANTS,
};
