import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize, timeout } from 'rxjs';
import { Restaurant } from '../restaurant/restaurant';
import { CardEnrichment, EnrichedPhoto } from './card-enrichment';

export const CARD_ENRICHMENT_URL = '/api/card-enrichment';
/** Enrichment requests at the same time (spec 007, plan PO-10 (1)). */
export const ENRICHMENT_CONCURRENCY = 3;
/** Browser safety limit of one request, counted from its sending (plan PO-4 (a)). */
export const ENRICHMENT_TIMEOUT_MS = 7000;

/** Card enrichment state of one website (specs/007-card-website-enrichment/data-model.md). */
type EnrichmentState =
  | { readonly status: 'queued' | 'pending' | 'none' }
  | { readonly status: 'photo'; readonly photo: EnrichedPhoto };

/** Loads an image in the browser without referrer (plan PO-12 (a)); true once it is displayable. */
function preload(url: string): Promise<boolean> {
  return new Promise((resolve) => {
    const image = new Image();
    image.referrerPolicy = 'no-referrer';
    image.onload = () => resolve(true);
    image.onerror = () => resolve(false);
    image.src = url;
  });
}

/**
 * Website photos of the deck cards (spec 007, FR-009, FR-010, FR-012). Kept for the whole session and
 * keyed by website (plan PO-13), apart from the deck state, which it never touches (research R9).
 */
@Injectable({ providedIn: 'root' })
export class CardEnrichmentStore {
  private readonly http = inject(HttpClient);
  private readonly states = signal<ReadonlyMap<string, EnrichmentState>>(new Map());
  /** Queued restaurants, first sent first; one per website. */
  private queue: Restaurant[] = [];
  private inFlight = 0;

  /**
   * Asks for the photo of the restaurants with a website and no data-source photo (FR-002), in the
   * given order: the first ones (the current card) go ahead of those already queued.
   */
  request(restaurants: readonly Restaurant[]): void {
    const wanted = restaurants.filter((r) => r.website && !r.photoUrl);
    const states = new Map(this.states());
    const first: Restaurant[] = [];
    for (const restaurant of wanted) {
      const website = restaurant.website!;
      if (!states.has(website)) {
        states.set(website, { status: 'queued' });
        first.push(restaurant);
      } else if (
        states.get(website)!.status === 'queued' &&
        !first.some((r) => r.website === website)
      ) {
        first.push(this.queue.find((r) => r.website === website)!);
      }
    }
    this.queue = [
      ...first,
      ...this.queue.filter((r) => !first.some((f) => f.website === r.website)),
    ];
    this.states.set(states);
    this.sendNext();
  }

  /** Photo of a website once loaded by the browser; undefined otherwise (the card keeps the tablecloth). */
  photo(website: string | undefined): EnrichedPhoto | undefined {
    const state = website ? this.states().get(website) : undefined;
    return state?.status === 'photo' ? state.photo : undefined;
  }

  private sendNext(): void {
    while (this.inFlight < ENRICHMENT_CONCURRENCY && this.queue.length) {
      this.send(this.queue.shift()!);
    }
  }

  private send(restaurant: Restaurant): void {
    const website = restaurant.website!;
    this.inFlight++;
    this.setState(website, { status: 'pending' });
    const params = new HttpParams({ fromObject: { website, cuisine: restaurant.cuisine } });
    this.http
      .get<CardEnrichment>(CARD_ENRICHMENT_URL, { params })
      .pipe(
        timeout(ENRICHMENT_TIMEOUT_MS),
        finalize(() => {
          this.inFlight--;
          this.sendNext();
        }),
      )
      .subscribe({
        next: (answer) => this.show(website, answer),
        // Failure, error status or timeout: the card silently keeps the tablecloth (FR-012).
        error: () => this.setState(website, { status: 'none' }),
      });
  }

  private show(website: string, answer: CardEnrichment | null): void {
    const url = answer?.photoUrl;
    if (!url) {
      this.setState(website, { status: 'none' });
      return;
    }
    void preload(url).then((loaded) =>
      this.setState(
        website,
        loaded ? { status: 'photo', photo: { url, credit: answer.credit } } : { status: 'none' },
      ),
    );
  }

  private setState(website: string, state: EnrichmentState): void {
    this.states.update((states) => new Map(states).set(website, state));
  }
}
