import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { provideNoWalkingRoute } from '../../map/walking-route.testing';
import { DECK_TEXTS, RESULTS_TEXTS } from '../../deck/deck.texts';
import { STUB_RESTAURANTS } from '../../deck/stub/stub-restaurants';
import { Restaurant } from '../restaurant';
import { RestaurantView, toRestaurantView } from '../restaurant-view';
import { RestaurantTileComponent } from './restaurant-tile.component';

@Component({
  imports: [RestaurantTileComponent],
  template: `
    <app-restaurant-tile
      [item]="item()"
      [decided]="decided()"
      [expanded]="expanded()"
      (toggleDetail)="toggles = toggles + 1"
    >
      <button type="button" data-testid="projected">Action</button>
    </app-restaurant-tile>
  `,
})
class HostComponent {
  readonly item = signal<RestaurantView>(toRestaurantView(STUB_RESTAURANTS[0], ''));
  readonly decided = signal(false);
  readonly expanded = signal(false);
  toggles = 0;
}

vi.mock('maplibre-gl', async () => (await import('../../map/maplibre-gl.mock')).maplibreMock);

describe('RestaurantTileComponent', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideNoWalkingRoute()],
    }),
  );

  function setup(restaurant: Restaurant = STUB_RESTAURANTS[0]) {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.item.set(toRestaurantView(restaurant, ''));
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    return { fixture, root, host: fixture.componentInstance };
  }

  it('shows the name, the cuisine, the distance and the projected action', () => {
    const { root } = setup();
    expect(root.querySelector('.card-name')?.textContent?.trim()).toBe(STUB_RESTAURANTS[0].name);
    expect(root.querySelector('.chip.cuisine')?.textContent?.trim()).toBe(
      STUB_RESTAURANTS[0].cuisine,
    );
    expect(root.querySelector('.fact.distance .fact-label')?.textContent?.trim()).toBe(
      RESULTS_TEXTS.distance,
    );
    expect(root.querySelector('.card-actions [data-testid="projected"]')).not.toBeNull();
  });

  it('shows the photo when there is one, else the tablecloth', () => {
    const withPhoto = setup({ ...STUB_RESTAURANTS[0], photoUrl: 'https://x/p.jpg' }).root;
    expect(withPhoto.querySelector('img.photo')).not.toBeNull();
    expect(withPhoto.querySelector('.tablecloth')).toBeNull();
    const without = setup({ ...STUB_RESTAURANTS[0], photoUrl: undefined }).root;
    expect(without.querySelector('.tablecloth')).not.toBeNull();
    expect(without.querySelector('img')).toBeNull();
  });

  it('shows the map instead of the tablecloth when there is no photo but a location', async () => {
    const { fixture, root } = setup({
      ...STUB_RESTAURANTS[0],
      photoUrl: undefined,
      location: { latitude: 48.85, longitude: 2.35 },
    });
    await fixture.whenStable();
    expect(root.querySelector('[data-testid="top-map"]')?.getAttribute('aria-label')).toBe(
      RESULTS_TEXTS.mapLabel(STUB_RESTAURANTS[0].name),
    );
    expect(root.querySelector('.tablecloth')).toBeNull();
  });

  it('keeps the photo over the map when both exist', () => {
    const { root } = setup({
      ...STUB_RESTAURANTS[0],
      photoUrl: 'https://x/p.jpg',
      location: { latitude: 48.85, longitude: 2.35 },
    });
    expect(root.querySelector('img.photo')).not.toBeNull();
    expect(root.querySelector('[data-testid="top-map"]')).toBeNull();
  });

  it('reads the suggestion badge, then "On y va" once decided', () => {
    const { fixture, root, host } = setup();
    expect(root.querySelector('.badge')?.textContent?.trim()).toBe(RESULTS_TEXTS.suggestion);
    host.decided.set(true);
    fixture.detectChanges();
    expect(root.querySelector('.badge')?.textContent?.trim()).toBe(DECK_TEXTS.pick);
    expect(root.querySelector('.badge.decided')).not.toBeNull();
  });

  it('emits toggleDetail and shows the detail only when expanded', () => {
    const { fixture, root, host } = setup();
    const button = () => root.querySelector<HTMLButtonElement>('.detail-btn')!;
    expect(root.querySelector('[data-testid="detail"]')).toBeNull();
    expect(button().textContent?.trim()).toBe(DECK_TEXTS.detail);
    button().click();
    expect(host.toggles).toBe(1);
    host.expanded.set(true);
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="detail"]')).not.toBeNull();
    expect(button().textContent?.trim()).toBe(DECK_TEXTS.hideDetail);
    expect(button().getAttribute('aria-expanded')).toBe('true');
  });
});
