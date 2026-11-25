/** Shapes of the answer of `POST /api/search` (specs/002-natural-language-search/contracts/search-api.openapi.yaml). */
export interface Position {
  readonly latitude: number;
  readonly longitude: number;
}

export interface OpeningPeriod {
  readonly opens: string;
  readonly closes: string;
}

/** Every field but the name is optional: the data source does not always provide it (spec 002, FR-012). */
export interface Restaurant {
  readonly name: string;
  readonly location?: Position;
  readonly distanceMeters?: number;
  readonly cuisine?: string;
  readonly diets?: readonly string[];
  readonly keywords?: readonly string[];
  readonly priceLevel?: string;
  readonly openingHours?: Readonly<Record<string, readonly OpeningPeriod[]>>;
  readonly photoUrl?: string;
  readonly signatureDish?: string;
  readonly address?: string;
  readonly phone?: string;
}

export interface SearchCriteria {
  readonly cuisines: readonly string[];
  readonly diets: readonly string[];
  readonly places: readonly string[];
  readonly moment?: string;
  readonly distanceLimitMeters?: number | null;
}

export interface SearchResult {
  readonly criteria: SearchCriteria;
  readonly deck: readonly Restaurant[];
  readonly zone?: readonly Restaurant[];
}

export type SearchErrorCode =
  | 'NOT_UNDERSTOOD'
  | 'NO_RESULT'
  | 'OUTSIDE_FRANCE'
  | 'PLACE_REQUIRED'
  | 'SERVICE_UNAVAILABLE';

/** A search that ended without result; `code` tells the screen which message to show. */
export class SearchFailure extends Error {
  constructor(readonly code: SearchErrorCode) {
    super(code);
  }
}
