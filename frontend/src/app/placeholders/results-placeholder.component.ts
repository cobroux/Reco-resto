import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DECK_TEXTS, REACTION_LABELS } from '../deck/deck.texts';
import { SessionReactionsService } from '../session/session-reactions.service';

/** Temporary results screen, replaced by feature 005 (decision D13). */
@Component({
  selector: 'app-results-placeholder',
  template: `
    <main class="placeholder">
      <h1>{{ texts.resultsTitle }}</h1>
      <ul>
        @for (item of items(); track item.id) {
          <li>{{ item.name }} — {{ item.label }}</li>
        }
      </ul>
    </main>
  `,
  styles: `
    .placeholder {
      max-width: 420px;
      margin: 0 auto;
      padding: 40px 20px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultsPlaceholderComponent {
  private readonly session = inject(SessionReactionsService);
  protected readonly texts = DECK_TEXTS;
  protected readonly items = computed(() =>
    this.session.reactions().map((reaction) => ({
      id: reaction.restaurantId,
      name: this.session.restaurant(reaction.restaurantId)?.name ?? reaction.restaurantId,
      label: REACTION_LABELS[reaction.kind],
    })),
  );
}
