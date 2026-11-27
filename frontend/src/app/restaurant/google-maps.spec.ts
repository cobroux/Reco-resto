import { googleMapsRouteUrl } from './google-maps';

describe('googleMapsRouteUrl', () => {
  it('asks Google Maps for a route to the restaurant name and address', () => {
    expect(googleMapsRouteUrl({ name: 'Sushi Ni', address: '40 Rue de Turenne, Paris' })).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=Sushi%20Ni%2C%2040%20Rue%20de%20Turenne%2C%20Paris',
    );
  });

  it('uses the name alone when the address is unknown', () => {
    expect(googleMapsRouteUrl({ name: 'Café & Co' })).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=Caf%C3%A9%20%26%20Co',
    );
  });
});
