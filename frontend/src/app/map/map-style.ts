import type { StyleSpecification } from 'maplibre-gl';

/**
 * Colours of the flat map of the Claude Design mockup (lavender ground, white roads, pale park and water). They are
 * the design tokens of `styles.css` (`--map-*`, and `--color-primary` for the route): MapLibre cannot read CSS
 * variables, so they are read from the page when the map starts.
 */
export interface MapColors {
  readonly ground: string;
  readonly building: string;
  readonly park: string;
  readonly water: string;
  readonly road: string;
  readonly route: string;
}

const TOKENS: Record<keyof MapColors, string> = {
  ground: '--map-ground',
  building: '--map-building',
  park: '--map-park',
  water: '--map-water',
  road: '--map-road',
  route: '--color-primary',
};

/** Reads the colours of the map from the design tokens in effect on `element`. */
export function readMapColors(element: Element): MapColors {
  const style = getComputedStyle(element);
  const read = (key: keyof MapColors) => style.getPropertyValue(TOKENS[key]).trim();
  return {
    ground: read('ground'),
    building: read('building'),
    park: read('park'),
    water: read('water'),
    road: read('road'),
    route: read('route'),
  };
}

const MAJOR_ROADS = ['motorway', 'trunk', 'primary', 'secondary', 'tertiary'];
const MINOR_ROADS = ['minor', 'service', 'busway', 'pedestrian', 'track'];

const roadWidth = (base: number) =>
  ['interpolate', ['exponential', 1.4], ['zoom'], 10, base * 0.3, 14, base, 18, base * 6] as never;

/** Own style: no labels, no icons, so the map stays calm under the markers. */
export function buildMapStyle(tilesUrl: string, colors: MapColors): StyleSpecification {
  return {
    version: 8,
    sources: {
      openmaptiles: { type: 'vector', url: tilesUrl },
      route: { type: 'geojson', data: { type: 'FeatureCollection', features: [] } },
    },
    layers: [
      { id: 'ground', type: 'background', paint: { 'background-color': colors.ground } },
      {
        id: 'green',
        type: 'fill',
        source: 'openmaptiles',
        'source-layer': 'landcover',
        filter: ['in', ['get', 'class'], ['literal', ['wood', 'grass']]],
        paint: { 'fill-color': colors.park },
      },
      {
        id: 'park',
        type: 'fill',
        source: 'openmaptiles',
        'source-layer': 'park',
        paint: { 'fill-color': colors.park },
      },
      {
        id: 'water',
        type: 'fill',
        source: 'openmaptiles',
        'source-layer': 'water',
        paint: { 'fill-color': colors.water },
      },
      {
        id: 'waterway',
        type: 'line',
        source: 'openmaptiles',
        'source-layer': 'waterway',
        paint: { 'line-color': colors.water, 'line-width': roadWidth(2) },
      },
      {
        id: 'building',
        type: 'fill',
        source: 'openmaptiles',
        'source-layer': 'building',
        minzoom: 14,
        paint: { 'fill-color': colors.building },
      },
      {
        id: 'roads-minor',
        type: 'line',
        source: 'openmaptiles',
        'source-layer': 'transportation',
        filter: ['in', ['get', 'class'], ['literal', MINOR_ROADS]],
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': colors.road, 'line-width': roadWidth(2) },
      },
      {
        id: 'roads-major',
        type: 'line',
        source: 'openmaptiles',
        'source-layer': 'transportation',
        filter: ['in', ['get', 'class'], ['literal', MAJOR_ROADS]],
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': colors.road, 'line-width': roadWidth(4) },
      },
      {
        id: 'route',
        type: 'line',
        source: 'route',
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': colors.route, 'line-width': 4, 'line-dasharray': [0.1, 2] },
      },
    ],
  };
}
