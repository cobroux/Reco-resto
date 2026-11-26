import { Observable } from 'rxjs';
import { Restaurant } from '../restaurant/restaurant';

export type TravelMode = 'walk' | 'bike' | 'car' | 'transit';

export interface RoutePreview {
  /** Only the modes with a possible route (spec 004, edge case); may be empty. */
  readonly modes: readonly { readonly mode: TravelMode; readonly minutes: number }[];
}

/**
 * Travel times from the user's position to a restaurant, shown on the back of the card (spec 004).
 * Feature 006 provides the real implementation (routing service, position request and its fallback).
 */
export abstract class RoutePreviewService {
  /** Emits once; may error, the preview then shows a message. */
  abstract preview(restaurant: Restaurant): Observable<RoutePreview>;
}
