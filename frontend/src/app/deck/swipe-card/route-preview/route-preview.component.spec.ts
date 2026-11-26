import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { Restaurant } from '../../../restaurant/restaurant';
import { DECK_TEXTS } from '../../deck.texts';
import { RoutePreview, RoutePreviewService } from '../../route-preview.service';
import { RoutePreviewComponent } from './route-preview.component';

const RESTAURANT: Restaurant = {
  id: 'stub-01',
  name: 'Trattoria Verde',
  distanceMeters: 350,
  cuisine: 'Italienne',
  keywords: [],
  weeklyHours: [],
};

describe('RoutePreviewComponent (spec 004, US2)', () => {
  let fixture: ComponentFixture<RoutePreviewComponent>;
  let root: HTMLElement;

  async function create(answer: Observable<RoutePreview>): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RoutePreviewService, useValue: { preview: () => answer } },
      ],
    });
    fixture = TestBed.createComponent(RoutePreviewComponent);
    fixture.componentRef.setInput('restaurant', RESTAURANT);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  /** One text per mode: its label and its time, as the two spans read side by side. */
  const lines = () =>
    [...root.querySelectorAll('.mode')].map((line) =>
      [...line.querySelectorAll('span')].map((span) => span.textContent?.trim()).join(' '),
    );

  it('shows the travel time of each available mode (AS1)', async () => {
    await create(
      of({
        modes: [
          { mode: 'walk', minutes: 5 },
          { mode: 'bike', minutes: 2 },
          { mode: 'car', minutes: 1 },
          { mode: 'transit', minutes: 7 },
        ],
      }),
    );

    expect(lines()).toEqual([
      `${DECK_TEXTS.travelModes.walk} 5 min`,
      `${DECK_TEXTS.travelModes.bike} 2 min`,
      `${DECK_TEXTS.travelModes.car} 1 min`,
      `${DECK_TEXTS.travelModes.transit} 7 min`,
    ]);
  });

  it('hides a mode that has no route (edge case)', async () => {
    await create(of({ modes: [{ mode: 'walk', minutes: 5 }] }));

    expect(lines()).toEqual([`${DECK_TEXTS.travelModes.walk} 5 min`]);
  });

  it('shows a waiting text until the answer comes', async () => {
    const pending = new Subject<RoutePreview>();
    await create(pending);
    expect(root.textContent).toContain(DECK_TEXTS.routeLoading);

    pending.next({ modes: [{ mode: 'walk', minutes: 5 }] });
    await fixture.whenStable();
    expect(root.textContent).not.toContain(DECK_TEXTS.routeLoading);
    expect(lines()).toHaveLength(1);
  });

  it('shows a message when the preview fails, and keeps the button to the full route', async () => {
    await create(throwError(() => new Error('down')));

    expect(root.querySelector('[role="status"]')?.textContent).toContain(DECK_TEXTS.routeUnavailable);
    expect(root.querySelector('a.full-route')).not.toBeNull();
  });

  it('has a button to the full route screen of this restaurant (FR-007)', async () => {
    await create(of({ modes: [] }));
    const link = root.querySelector('a.full-route');

    expect(link?.getAttribute('href')).toBe('/route/stub-01');
    expect(link?.textContent).toContain(DECK_TEXTS.fullRoute);
  });
});
