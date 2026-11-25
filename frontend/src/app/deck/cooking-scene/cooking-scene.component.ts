import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { COOKING_TOSS_MS } from '../cooking-toss.token';
import { DECK_TEXTS } from '../deck.texts';

/**
 * "En cuisine…" crêpe toss shown on the tablecloth under the current card (FR-017, D21).
 * Announced to assistive technologies only while the next card is being prepared.
 */
@Component({
  selector: 'app-cooking-scene',
  templateUrl: './cooking-scene.component.html',
  styleUrl: './cooking-scene.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookingSceneComponent {
  /** True while the next card is being prepared. */
  readonly waiting = input(false);

  protected readonly texts = DECK_TEXTS;
  /** Duration of one toss, passed to the CSS animation as --toss-ms. */
  protected readonly tossMs = inject(COOKING_TOSS_MS);
}
