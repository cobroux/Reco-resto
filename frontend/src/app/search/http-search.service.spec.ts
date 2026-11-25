import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { HttpSearchService } from './http-search.service';
import { SearchFailure } from './search-result';

const SENTENCE = 'asiatique végétarien près d’Opéra';
const EMPTY_RESULT = { criteria: { cuisines: [], diets: [], places: [] }, deck: [] };

describe('HttpSearchService', () => {
  let service: HttpSearchService;
  let http: HttpTestingController;

  function geolocation(getCurrentPosition: unknown): void {
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [HttpSearchService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HttpSearchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts the sentence unchanged with the position when it is granted', async () => {
    geolocation((success: PositionCallback) =>
      success({ coords: { latitude: 48.85, longitude: 2.35 } } as GeolocationPosition),
    );

    service.search(SENTENCE).subscribe();

    const request = await vi.waitFor(() => http.expectOne('/api/search'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      sentence: SENTENCE,
      position: { latitude: 48.85, longitude: 2.35 },
    });
    request.flush(EMPTY_RESULT);
  });

  it('searches without position when it is refused', async () => {
    geolocation((_success: PositionCallback, failure: PositionErrorCallback) =>
      failure({ code: 1 } as GeolocationPositionError),
    );

    service.search(SENTENCE).subscribe();

    const request = await vi.waitFor(() => http.expectOne('/api/search'));
    expect(request.request.body).toEqual({ sentence: SENTENCE });
    request.flush(EMPTY_RESULT);
  });

  it('searches without position when geolocation does not exist', async () => {
    vi.stubGlobal('navigator', {});

    service.search(SENTENCE).subscribe();

    const request = await vi.waitFor(() => http.expectOne('/api/search'));
    expect(request.request.body).toEqual({ sentence: SENTENCE });
    request.flush(EMPTY_RESULT);
  });

  it('emits the result once and completes when the answer arrives', async () => {
    vi.stubGlobal('navigator', {});
    const next = vi.fn();
    const complete = vi.fn();
    const result = {
      criteria: { cuisines: [], diets: [], places: [], moment: '2026-11-28T12:00' },
      deck: [{ name: 'Thai Garden', address: '3 rue X', distanceMeters: 200, cuisine: 'thai' }],
    };

    service.search(SENTENCE).subscribe({ next, complete });

    const request = await vi.waitFor(() => http.expectOne('/api/search'));
    expect(complete).not.toHaveBeenCalled();
    request.flush(result);
    expect(next).toHaveBeenCalledTimes(1);
    // the swipe deck (feature 003) gets the result as a deck input
    expect(next).toHaveBeenCalledWith({
      momentLabel: null,
      targetDateTime: '2026-11-28T12:00:00+01:00',
      restaurants: [
        expect.objectContaining({ id: 'Thai Garden|3 rue X', name: 'Thai Garden', distanceMeters: 200 }),
      ],
    });
    expect(complete).toHaveBeenCalledTimes(1);
  });

  it.each([['NOT_UNDERSTOOD'], ['NO_RESULT'], ['OUTSIDE_FRANCE'], ['PLACE_REQUIRED']])(
    'reports the backend error code %s as a SearchFailure',
    async (code) => {
      vi.stubGlobal('navigator', {});
      const error = vi.fn();

      service.search(SENTENCE).subscribe({ error });

      const request = await vi.waitFor(() => http.expectOne('/api/search'));
      request.flush({ code }, { status: 422, statusText: 'Unprocessable Entity' });
      expect(error).toHaveBeenCalledTimes(1);
      expect(error.mock.calls[0][0]).toBeInstanceOf(SearchFailure);
      expect(error.mock.calls[0][0].code).toBe(code);
    },
  );

  it.each([
    [503, { code: 'SERVICE_UNAVAILABLE' }],
    [500, null],
    [400, { status: 400 }],
  ])('reports status %s without a known code as SERVICE_UNAVAILABLE', async (status, body) => {
    vi.stubGlobal('navigator', {});
    const error = vi.fn();

    service.search(SENTENCE).subscribe({ error });

    const request = await vi.waitFor(() => http.expectOne('/api/search'));
    request.flush(body, { status, statusText: 'Error' });
    expect(error.mock.calls[0][0].code).toBe('SERVICE_UNAVAILABLE');
  });

  it('reports a network failure as SERVICE_UNAVAILABLE', async () => {
    vi.stubGlobal('navigator', {});
    const error = vi.fn();

    service.search(SENTENCE).subscribe({ error });

    const request = await vi.waitFor(() => http.expectOne('/api/search'));
    request.error(new ProgressEvent('error'));
    expect(error.mock.calls[0][0].code).toBe('SERVICE_UNAVAILABLE');
  });
});
