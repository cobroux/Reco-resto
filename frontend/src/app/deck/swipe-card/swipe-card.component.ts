import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  InjectionToken,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Restaurant } from '../../restaurant/restaurant';
import { DECK_TEXTS } from '../deck.texts';
import { formatDistance } from '../format/distance';
import { closingTimeAt } from '../format/opening';
import { prefersReducedMotion } from '../motion';
import { SWIPE_THRESHOLD_PX, SwipeDirection, classifySwipe, isDragging } from './swipe-gesture';

type Direction = Exclude<SwipeDirection, 'none'>;

/** Duration of the leave animation (decision D18): 300 ms, none when reduced motion is preferred. */
export function animationDuration(win: Pick<Window, 'matchMedia'> | undefined): number {
  return prefersReducedMotion(win) ? 0 : 300;
}

export const SWIPE_ANIMATION_MS = new InjectionToken<number>('SWIPE_ANIMATION_MS', {
  factory: () => animationDuration(globalThis.window),
});

/** Off-screen end of the leave animation, from where the card was released. */
function leavingTransform(direction: Direction, dx: number, dy: number): string {
  switch (direction) {
    case 'left':
      return `translate(-120vw, ${dy}px) rotate(-30deg)`;
    case 'right':
      return `translate(120vw, ${dy}px) rotate(30deg)`;
    case 'up':
      return `translate(${dx}px, -120vh) rotate(${dx / 18}deg)`;
  }
}

/** Front of one deck card, draggable with mouse or finger (FR-003, FR-005). */
@Component({
  selector: 'app-swipe-card',
  templateUrl: './swipe-card.component.html',
  styleUrl: './swipe-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SwipeCardComponent {
  readonly restaurant = input.required<Restaurant>();
  readonly targetDateTime = input.required<string>();
  readonly disabled = input(false);
  /** A reaction was chosen (swipe or `leave()`); the card starts flying off. */
  readonly swiped = output<Direction>();
  /** The card has flown off: the reaction can be applied (D18). */
  readonly left = output<Direction>();

  private readonly animationMs = inject(SWIPE_ANIMATION_MS);
  private leaveTimer?: ReturnType<typeof setTimeout>;

  protected readonly texts = DECK_TEXTS;
  protected readonly distance = computed(() => formatDistance(this.restaurant().distanceMeters));
  protected readonly closingTime = computed(() =>
    closingTimeAt(this.restaurant().weeklyHours, this.targetDateTime()),
  );

  private start: { x: number; y: number } | null = null;
  private readonly dx = signal(0);
  private readonly dy = signal(0);
  private readonly dragging = computed(() => isDragging(this.dx(), this.dy()));

  private readonly leaving = signal<Direction | null>(null);
  protected readonly isLeaving = computed(() => this.leaving() !== null);

  protected readonly transform = computed(() => {
    const leaving = this.leaving();
    if (leaving) {
      return leavingTransform(leaving, this.dx(), this.dy());
    }
    return this.dragging()
      ? `translate(${this.dx()}px, ${this.dy()}px) rotate(${this.dx() / 18}deg)`
      : '';
  });
  protected readonly transition = computed(() =>
    this.isLeaving()
      ? `transform ${this.animationMs}ms ease-in, opacity ${this.animationMs}ms ease-in`
      : '',
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.leaveTimer));
  }

  /** Flies the card off in a direction, as for a swipe (used by the reaction buttons). */
  leave(direction: Direction): void {
    if (this.leaving() || this.disabled()) {
      return;
    }
    this.start = null;
    this.leaving.set(direction);
    this.swiped.emit(direction);
    this.leaveTimer = setTimeout(() => this.left.emit(direction), this.animationMs);
  }
  protected readonly likeOpacity = computed(() => this.stamp(this.dx()));
  protected readonly nopeOpacity = computed(() => this.stamp(-this.dx()));

  private stamp(distance: number): number {
    return this.dragging() ? Math.max(0, Math.min(1, distance / SWIPE_THRESHOLD_PX)) : 0;
  }

  protected onPointerDown(event: PointerEvent): void {
    // Main button or a single finger only (no right click, no second finger).
    if (this.leaving() || this.disabled() || event.button !== 0 || event.isPrimary === false) {
      return;
    }
    this.start = { x: event.clientX, y: event.clientY };
    // Keep receiving the pointer even when it leaves the card, so the release is never lost.
    const target = event.currentTarget as Element | null;
    if (target?.setPointerCapture && event.pointerId !== undefined) {
      try {
        target.setPointerCapture(event.pointerId);
      } catch {
        // Capture is a comfort only; the lost-button check below covers its absence.
      }
    }
  }

  protected onPointerMove(event: PointerEvent): void {
    if (!this.start) {
      return;
    }
    if (event.buttons === 0) {
      // The button was released where the card could not see it: drop the drag.
      this.reset();
      return;
    }
    this.dx.set(event.clientX - this.start.x);
    this.dy.set(event.clientY - this.start.y);
  }

  protected onPointerUp(): void {
    if (!this.start) {
      return;
    }
    const direction = classifySwipe(this.dx(), this.dy());
    if (direction === 'none') {
      this.reset();
      return;
    }
    this.leave(direction);
  }

  private reset(): void {
    this.start = null;
    this.dx.set(0);
    this.dy.set(0);
  }
}
