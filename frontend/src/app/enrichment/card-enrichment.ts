/** Origin of an enrichment photo (contract `CardEnrichment.photoOrigin`). */
export type PhotoOrigin = 'SHARING_IMAGE' | 'LARGEST_PAGE_IMAGE';

/**
 * Answer of `GET /api/card-enrichment` (specs/007-card-website-enrichment/contracts/card-enrichment.openapi.yaml).
 * `{}` means "keep the tablecloth".
 */
export interface CardEnrichment {
  readonly photoUrl?: string;
  readonly photoOrigin?: PhotoOrigin;
}

/** Photo of a card once the browser has loaded it. */
export interface EnrichedPhoto {
  readonly url: string;
}
