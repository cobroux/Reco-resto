const TITLE_START = 'Où manger,';
const TITLE_END = '?';
const TITLE_PHRASES = ['juste à côté', 'ce midi', 'ce soir', 'en terrasse'] as const;

/** Delay between two title phrases (value given by the product owner: 5 seconds). */
export const TITLE_ROTATION_MS = 5000;

/** Single source of every user-facing text of the home screen (spec FR-001, FR-004, FR-007). */
export const HOME_TEXTS = {
  logo: 'Nappe',
  badge: 'Restaurants autour de vous',
  /** Accessible name of the heading; the visible phrase rotates (see `titlePhrases`). */
  title: `${TITLE_START} ${TITLE_PHRASES[0]} ${TITLE_END}`,
  titleStart: TITLE_START,
  /** Phrases shown in turn after `titleStart` (wording to be validated by the product owner). */
  titlePhrases: TITLE_PHRASES,
  titleEnd: TITLE_END,
  subtitle: 'Trouvez un restaurant adapté à votre régime et à vos envies, en quelques secondes.',
  placeholder: 'Dites-nous ce qui vous ferait plaisir, et où',
  submit: 'Commencer',
  emptyError: "Votre recherche est vide. Ex. : asiatique végétarien près d'Opéra",
  // Wording not specified in the spec: to be validated by the product owner.
  searching: 'Recherche en cours…',
  // Wording not specified in specs 001, 002 or 003 (T5): to be validated by the product owner.
  searchError: 'La recherche a échoué. Veuillez réessayer.',
} as const;

export interface DietTile {
  readonly label: string;
  readonly background: string;
  readonly iconColor: string;
  readonly iconBackground: string;
  /** SVG path data (24x24 viewBox, stroke icon) taken from the mockup. */
  readonly iconPaths: readonly string[];
}

/** Decorative dietary tiles (spec FR-007); colors and icons from the Claude Design mockup. */
export const DIET_TILES: readonly DietTile[] = [
  {
    label: 'Vegan',
    background: '#E3F5E1',
    iconColor: '#2F7A3A',
    iconBackground: '#FFFFFF',
    iconPaths: ['M11 20A7 7 0 0 1 4 13c0-6 7-9 16-9 0 9-3 16-9 16z', 'M4 20c4-4 7-6 11-8'],
  },
  {
    label: 'Végétarien',
    background: '#FDEBDD',
    iconColor: '#A24E12',
    iconBackground: '#FFFFFF',
    iconPaths: ['M12 21v-9M12 12c0-4-3-7-8-7 0 4 3 7 8 7zM12 12c0-3 2-6 7-6 0 3-2 6-7 6z'],
  },
  {
    label: 'Sans gluten',
    background: '#FCE4EA',
    iconColor: '#B0284A',
    iconBackground: '#FFFFFF',
    iconPaths: [
      'M12 22V8M12 8c-2-1-3-3-3-5 2 0 3 2 3 5zM12 8c2-1 3-3 3-5-2 0-3 2-3 5zM12 14c-2-1-4-2-4-5 2 0 4 2 4 5zM12 14c2-1 4-2 4-5-2 0-4 2-4 5z',
    ],
  },
  {
    label: 'Halal, casher…',
    background: '#FFFFFF',
    iconColor: '#5B4BC4',
    iconBackground: '#F5F3FC',
    iconPaths: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z', 'm8 12 3 3 5-6'],
  },
];
