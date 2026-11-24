import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DECK_TEXTS } from '../deck/deck.texts';
import { DeckStore } from '../deck/deck.store';
import { SessionReactionsService } from '../session/session-reactions.service';

/** Temporary route screen, replaced by feature 006 (decision D13). */
@Component({
  selector: 'app-route-placeholder',
  template: `
    <main class="placeholder">
      <h1>{{ restaurant?.name }}</h1>
      <button type="button" (click)="store.returnFromPick()">{{ texts.backToDeck }}</button>
      <button type="button" (click)="openList()">{{ texts.seeList }}</button>
    </main>
  `,
  styles: `
    .placeholder {
      max-width: 420px;
      margin: 0 auto;
      padding: 40px 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoutePlaceholderComponent {
  protected readonly store = inject(DeckStore);
  private readonly router = inject(Router);
  protected readonly texts = DECK_TEXTS;
  protected readonly restaurant = inject(SessionReactionsService).restaurant(
    inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '',
  );

  protected openList(): void {
    void this.router.navigate(['/results']);
  }
}
