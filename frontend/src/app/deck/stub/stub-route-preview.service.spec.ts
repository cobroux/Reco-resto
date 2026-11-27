import { firstValueFrom } from 'rxjs';
import { Restaurant } from '../../restaurant/restaurant';
import { StubRoutePreviewService } from './stub-route-preview.service';

function restaurantAt(distanceMeters: number): Restaurant {
  return {
    id: 'r1',
    name: 'Baan Thaï',
    distanceMeters,
    cuisine: 'Thaïlandaise',
    keywords: [],
    weeklyHours: [],
  };
}

describe('StubRoutePreviewService (stub values, plan D-04-6, to validate)', () => {
  const service = new StubRoutePreviewService();

  it('derives the travel time of each mode from the distance, rounded up to the minute', async () => {
    const preview = await firstValueFrom(service.preview(restaurantAt(1100)));

    expect(preview.modes).toEqual([
      { mode: 'walk', minutes: 14 },
      { mode: 'bike', minutes: 5 },
      { mode: 'car', minutes: 3 },
      { mode: 'transit', minutes: 9 },
    ]);
  });

  it('has no public transport for a very short distance, so the mode is hidden', async () => {
    const preview = await firstValueFrom(service.preview(restaurantAt(350)));

    expect(preview.modes.map((m) => m.mode)).toEqual(['walk', 'bike', 'car']);
  });
});
