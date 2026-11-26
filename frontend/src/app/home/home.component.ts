import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { SearchResultStore } from '../search/search-result.store';
import { SearchService } from '../search/search.service';
import { DIET_TILES, HOME_TEXTS, TITLE_ROTATION_MS } from './home.texts';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly searchService = inject(SearchService);
  private readonly results = inject(SearchResultStore);
  private readonly router = inject(Router);

  protected readonly texts = HOME_TEXTS;
  protected readonly tiles = DIET_TILES;

  /** Current field value; empty on every opening (data-model: Screen state). */
  protected readonly sentence = signal('');
  /** True while a search runs; blocks a second submission (FR-006). */
  protected readonly searching = signal(false);
  /** True after an empty submission (FR-004); reset when the user types. */
  protected readonly emptyError = signal(false);
  /** True after a failed search (spec 003 FR-019, T5); reset when the user types or submits again. */
  protected readonly searchError = signal(false);

  private readonly phraseIndex = signal(0);
  /** Phrase shown after "Où manger," ; changes every {@link TITLE_ROTATION_MS}. */
  protected readonly currentPhrase = computed(() => this.texts.titlePhrases[this.phraseIndex()]);

  constructor() {
    const timer = setInterval(
      () => this.phraseIndex.update((index) => (index + 1) % this.texts.titlePhrases.length),
      TITLE_ROTATION_MS,
    );
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected onInput(event: Event): void {
    this.sentence.set((event.target as HTMLInputElement).value);
    this.emptyError.set(false);
    this.searchError.set(false);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (this.searching()) {
      return;
    }
    // Emptiness is tested on the trimmed value, but the original text is submitted (FR-004, FR-005).
    if (this.sentence().trim() === '') {
      this.emptyError.set(true);
      return;
    }
    this.emptyError.set(false);
    this.searchError.set(false);
    this.searching.set(true);
    this.searchService
      .search(this.sentence())
      .pipe(finalize(() => this.searching.set(false)))
      .subscribe({
        // The deck opens on the result of this search (spec 003 FR-019, FR-020).
        next: (result) => {
          this.results.set(result);
          void this.router.navigate(['/deck']);
        },
        error: () => this.searchError.set(true),
      });
  }
}
