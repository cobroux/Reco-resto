import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Restaurant } from '../../restaurant/restaurant';
import { RoutePreview, RoutePreviewService, TravelMode } from '../route-preview.service';

/**
 * Stub values, NOT validated business data (plan D-04-6): speeds in km/h, a fixed wait for public
 * transport, and no public transport under 500 m. Replaced by feature 006's routing.
 */
const SPEED_KMH: Record<TravelMode, number> = { walk: 5, bike: 15, car: 30, transit: 20 };
const TRANSIT_WAIT_MIN = 5;
const TRANSIT_MIN_DISTANCE_M = 500;

/** Temporary route preview, derived from the distance only (the user's position is not used). */
@Injectable()
export class StubRoutePreviewService extends RoutePreviewService {
  override preview(restaurant: Restaurant): Observable<RoutePreview> {
    const km = restaurant.distanceMeters / 1000;
    const minutes = (mode: TravelMode) =>
      Math.ceil((km / SPEED_KMH[mode]) * 60 + (mode === 'transit' ? TRANSIT_WAIT_MIN : 0));
    const modes = (['walk', 'bike', 'car', 'transit'] as const)
      .filter((mode) => mode !== 'transit' || restaurant.distanceMeters >= TRANSIT_MIN_DISTANCE_M)
      .map((mode) => ({ mode, minutes: minutes(mode) }));
    return of({ modes });
  }
}
