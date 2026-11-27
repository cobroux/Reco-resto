/** True when the user asked the system to reduce motion; false when it cannot be known. */
export function prefersReducedMotion(win: Pick<Window, 'matchMedia'> | undefined): boolean {
  return win?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}
