import { Provider } from '@angular/core';
import { of } from 'rxjs';
import { WalkingRouteService } from './walking-route.service';

/** Test double of the routing service that finds nothing, so the specs never reach the network. */
export function provideNoWalkingRoute(): Provider {
  return {
    provide: WalkingRouteService,
    useValue: { route: () => of(null), distances: () => of([]) },
  };
}
