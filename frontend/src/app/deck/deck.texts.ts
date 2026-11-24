import { ReactionKind } from './deck.types';

/** Single source of every French text of the swipe deck and its placeholder screens. */
export const DECK_TEXTS = {
  logo: 'Nappe',
  title: (moment: string | null) => `Ça vous dit, ${moment ?? 'maintenant'} ?`,
  progress: (position: number, size: number) => `${position} sur ${size}`,
  seeList: 'Voir la liste',
  dislike: 'Pas envie',
  like: 'Miam',
  pick: 'On y va',
  rewind: 'Revenir à la carte précédente',
  toTaste: 'À goûter',
  openUntil: (time: string) => `Ouvert jusqu'à ${time}`,
  degraded: 'Suggestions non personnalisées pour le moment',
  resultsTitle: 'Résultats — bientôt disponible',
  backToDeck: 'Retour aux propositions',
} as const;

/** Label of each reaction, reused from the deck buttons. */
export const REACTION_LABELS: Record<ReactionKind, string> = {
  disliked: DECK_TEXTS.dislike,
  liked: DECK_TEXTS.like,
  picked: DECK_TEXTS.pick,
};
