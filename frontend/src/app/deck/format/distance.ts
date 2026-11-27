/**
 * Distance shown on a card (decision D15): below 1 km in metres rounded to 10 m ("350 m");
 * otherwise in kilometres with one decimal and a French comma ("1,1 km"). A distance that rounds
 * to 1000 m is shown as "1,0 km".
 */
export function formatDistance(meters: number): string {
  const rounded = Math.round(meters / 10) * 10;
  if (rounded < 1000) {
    return `${rounded} m`;
  }
  return `${(meters / 1000).toFixed(1).replace('.', ',')} km`;
}
