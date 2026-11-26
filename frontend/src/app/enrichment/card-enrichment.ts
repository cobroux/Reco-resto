/** Unsplash credit of a cuisine photo (spec 007 FR-015, contract `PhotoCredit`). */
export interface PhotoCredit {
  readonly photographerName: string;
  readonly photographerUrl: string;
  readonly unsplashUrl: string;
}

/** Origin of an enrichment photo (contract `CardEnrichment.photoOrigin`). */
export type PhotoOrigin = 'SHARING_IMAGE' | 'LARGEST_PAGE_IMAGE' | 'UNSPLASH_CUISINE_PHOTO';

/**
 * Answer of `GET /api/card-enrichment` (specs/007-card-website-enrichment/contracts/card-enrichment.openapi.yaml).
 * `{}` means "keep the tablecloth".
 */
export interface CardEnrichment {
  readonly photoUrl?: string;
  readonly photoOrigin?: PhotoOrigin;
  /** Present exactly when `photoOrigin` is `UNSPLASH_CUISINE_PHOTO`. */
  readonly credit?: PhotoCredit;
}

/** Photo of a card once the browser has loaded it, with its Unsplash credit when it has one. */
export interface EnrichedPhoto {
  readonly url: string;
  readonly credit?: PhotoCredit;
}
