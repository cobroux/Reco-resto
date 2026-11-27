/** Thresholds of decision D5 (horizontal values read from the mockup). */
export const DRAG_START_PX = 6;
export const SWIPE_THRESHOLD_PX = 110;

export type SwipeDirection = 'left' | 'right' | 'up' | 'none';

/** True once the pointer moved far enough for the card to follow it. */
export function isDragging(dx: number, dy: number): boolean {
  return Math.max(Math.abs(dx), Math.abs(dy)) > DRAG_START_PX;
}

/**
 * Direction of a released drag. Up needs the vertical movement to dominate; a downward movement
 * is never a swipe.
 */
export function classifySwipe(dx: number, dy: number): SwipeDirection {
  if (-dy > Math.abs(dx)) {
    return -dy >= SWIPE_THRESHOLD_PX ? 'up' : 'none';
  }
  if (dx >= SWIPE_THRESHOLD_PX) {
    return 'right';
  }
  if (dx <= -SWIPE_THRESHOLD_PX) {
    return 'left';
  }
  return 'none';
}
