import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { SearchService } from '../search/search.service';
import { DIET_TILES, HOME_TEXTS } from './home.texts';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly searchService = inject(SearchService);

  protected readonly texts = HOME_TEXTS;
  protected readonly tiles = DIET_TILES;

  /** Current field value; empty on every opening (data-model: Screen state). */
  protected readonly sentence = signal('');
  /** True while a search runs; blocks a second submission (FR-006). */
  protected readonly searching = signal(false);
  /** True after an empty submission (FR-004); reset when the user types. */
  protected readonly emptyError = signal(false);

  protected onInput(event: Event): void {
    this.sentence.set((event.target as HTMLInputElement).value);
    this.emptyError.set(false);
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
    this.searching.set(true);
    this.searchService
      .search(this.sentence())
      .pipe(finalize(() => this.searching.set(false)))
      .subscribe();
  }
}
