import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { STUB_RESTAURANTS } from '../deck/stub/stub-restaurants';
import { Restaurant } from '../restaurant/restaurant';
import {
  CARD_ENRICHMENT_URL,
  ENRICHMENT_CONCURRENCY,
  ENRICHMENT_TIMEOUT_MS,
  CardEnrichmentStore,
} from './card-enrichment.store';

/** Browser image used by the store to preload a photo; tests decide when it loads or fails. */
class FakeImage {
  static instances: FakeImage[] = [];
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  referrerPolicy = '';
  src = '';
  constructor() {
    FakeImage.instances.push(this);
  }
  static last(): FakeImage {
    return FakeImage.instances.at(-1)!;
  }
}

/** Restaurant with a website and no data-source photo (spec 007 FR-002). */
function withWebsite(index: number, website = `https://resto-${index}.example/`): Restaurant {
  return { ...STUB_RESTAURANTS[index], photoUrl: undefined, website };
}

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('CardEnrichmentStore (spec 007, T25, T61)', () => {
  let store: CardEnrichmentStore;
  let http: HttpTestingController;

  beforeEach(() => {
    FakeImage.instances = [];
    vi.stubGlobal('Image', FakeImage);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(CardEnrichmentStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  /** Requests sent since the previous call (`match` hands each request once). */
  const pending = () => http.match((request) => request.url === CARD_ENRICHMENT_URL);

  it('uses the decided limits: 3 requests at a time (PO-10 (1)), 7 s browser limit (PO-4 (a))', () => {
    expect(ENRICHMENT_CONCURRENCY).toBe(3);
    expect(ENRICHMENT_TIMEOUT_MS).toBe(7000);
  });

  it('requests GET /api/card-enrichment with the encoded website and cuisine (FR-013, T61)', () => {
    const restaurant = withWebsite(0, 'https://x.example/a b?c=1&d=2');

    store.request([restaurant]);

    const [request] = pending();
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('website')).toBe('https://x.example/a b?c=1&d=2');
    expect(request.request.params.keys()).toEqual(['website']);
    expect(request.request.urlWithParams).toContain('a%20b');
    expect(request.request.urlWithParams).toContain('%26d');
  });

  it('requests only restaurants with a website and no photo (T4, T32, FR-002)', () => {
    store.request([
      STUB_RESTAURANTS[0],
      { ...STUB_RESTAURANTS[1], website: 'https://with-photo.example/' },
      { ...STUB_RESTAURANTS[2], photoUrl: undefined },
    ]);

    expect(pending().length).toBe(0);
  });

  it('sends one request per distinct website (chains share it)', () => {
    store.request([
      withWebsite(0, 'https://chain.example/'),
      withWebsite(1, 'https://chain.example/'),
    ]);
    store.request([withWebsite(2, 'https://chain.example/')]);

    expect(pending().length).toBe(1);
  });

  it('keeps at most 3 requests in flight, the others queued, and sends the next when one ends', () => {
    store.request([0, 1, 2, 3, 4].map((i) => withWebsite(i)));

    const first = pending();
    expect(first.map((r) => r.request.params.get('website'))).toEqual([
      'https://resto-0.example/',
      'https://resto-1.example/',
      'https://resto-2.example/',
    ]);

    first[1].flush({});
    // `match` hands each request once: only the newly sent one is returned.
    expect(pending().map((r) => r.request.params.get('website'))).toEqual([
      'https://resto-3.example/',
    ]);
  });

  it('sends the current card first: the first restaurant of a later call moves ahead of the queue', () => {
    store.request([0, 1, 2, 3, 4].map((i) => withWebsite(i)));
    store.request([withWebsite(4), withWebsite(3)]);

    pending()[0].flush({});

    expect(pending().map((r) => r.request.params.get('website'))).toEqual([
      'https://resto-4.example/',
    ]);
  });

  it('gives the photo once the browser has loaded it, never before (T5, FR-010)', async () => {
    const restaurant = withWebsite(0);
    store.request([restaurant]);

    pending()[0].flush({ photoUrl: 'https://cdn.example/salle.jpg', photoOrigin: 'SHARING_IMAGE' });
    expect(store.photo(restaurant.website)).toBeUndefined();
    expect(FakeImage.last().src).toBe('https://cdn.example/salle.jpg');
    expect(FakeImage.last().referrerPolicy).toBe('no-referrer');

    FakeImage.last().onload!();
    await flushPromises();

    expect(store.photo(restaurant.website)).toEqual({ url: 'https://cdn.example/salle.jpg' });
  });

  it('gives no photo when the browser cannot load the image (T9)', async () => {
    const restaurant = withWebsite(0);
    store.request([restaurant]);

    pending()[0].flush({
      photoUrl: 'https://cdn.example/broken.jpg',
      photoOrigin: 'SHARING_IMAGE',
    });
    FakeImage.last().onerror!();
    await flushPromises();

    expect(store.photo(restaurant.website)).toBeUndefined();
  });

  it('gives no photo for {}, a non-200 answer or a network error, and goes on with the others (T26, T29)', async () => {
    const restaurants = [0, 1, 2].map((i) => withWebsite(i));
    store.request(restaurants);
    const [empty, failing, network] = pending();

    empty.flush({});
    failing.flush(null, { status: 500, statusText: 'Server Error' });
    network.error(new ProgressEvent('error'));
    await flushPromises();

    restaurants.forEach((r) => expect(store.photo(r.website)).toBeUndefined());
    expect(FakeImage.instances.length).toBe(0);
  });

  it('gives up after 7 s without answer and frees the slot (PO-4 (a))', () => {
    vi.useFakeTimers();
    store.request([0, 1, 2, 3].map((i) => withWebsite(i)));
    const first = pending()[0];

    vi.advanceTimersByTime(ENRICHMENT_TIMEOUT_MS - 1);
    expect(first.cancelled).toBe(false);
    vi.advanceTimersByTime(1);

    expect(first.cancelled).toBe(true);
    expect(store.photo('https://resto-0.example/')).toBeUndefined();
    expect(pending().map((r) => r.request.params.get('website'))).toContain(
      'https://resto-3.example/',
    );
  });

  it('keeps results for the session: the same website in a later search sends no new request (PO-13)', async () => {
    store.request([withWebsite(0, 'https://kept.example/')]);
    pending()[0].flush({});
    await flushPromises();

    store.request([withWebsite(5, 'https://kept.example/')]);

    expect(pending().length).toBe(0);
  });
});
