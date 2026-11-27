import { TestBed } from '@angular/core/testing';
import { STUB_RESTAURANTS } from '../../deck/stub/stub-restaurants';
import { toRestaurantView } from '../restaurant-view';
import { RestaurantDetailComponent } from './restaurant-detail.component';

describe('RestaurantDetailComponent', () => {
  it('shows the address, the phone and the weekly hours', () => {
    const restaurant = { ...STUB_RESTAURANTS[0], address: '1 rue X', phone: '01 23' };
    const fixture = TestBed.createComponent(RestaurantDetailComponent);
    fixture.componentRef.setInput('item', toRestaurantView(restaurant, ''));
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('[data-testid="detail-address"]')?.textContent).toContain('1 rue X');
    expect(root.querySelector('[data-testid="detail-phone"]')?.textContent).toContain('01 23');
    expect(root.querySelectorAll('.hours-row').length).toBe(7);
  });

  it('highlights only the targeted day in the weekly hours', () => {
    const fixture = TestBed.createComponent(RestaurantDetailComponent);
    fixture.componentRef.setInput(
      'item',
      toRestaurantView(STUB_RESTAURANTS[0], '2026-11-27T20:00:00+01:00'),
    );
    fixture.detectChanges();
    const targeted = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.hours-row.targeted')];
    expect(targeted).toHaveLength(1);
    expect(targeted[0].textContent).toContain('Vendredi');
  });
});
