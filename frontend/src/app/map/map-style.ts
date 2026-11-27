import type { StyleSpecification } from 'maplibre-gl';

/** Vector tiles of OpenFreeMap (OpenStreetMap data, free, no API key); its TileJSON carries the attribution. */
export const TILES_URL = 'https://tiles.openfreemap.org/planet';

/** Colours of the flat map of the Claude Design mockup (lavender ground, white roads, pale park and water). */
export const MAP_COLORS = {
  ground: '#e9e6f4',
  building: '#e1def0',
  park: '#e0f1df',
  water: '#d9e6f8',
  road: '#ffffff',
  route: '#5b4bc4',
} as const;

const MAJOR_ROADS = ['motorway', 'trunk', 'primary', 'secondary', 'tertiary'];
const MINOR_ROADS = ['minor', 'service', 'busway', 'pedestrian', 'track'];

const roadWidth = (base: number) =>
  ['interpolate', ['exponential', 1.4], ['zoom'], 10, base * 0.3, 14, base, 18, base * 6] as never;

/** Own style: no labels, no icons, so the map stays calm under the markers. */
export const MAP_STYLE: StyleSpecification = {
  version: 8,
  sources: {
    openmaptiles: { type: 'vector', url: TILES_URL },
    route: { type: 'geojson', data: { type: 'FeatureCollection', features: [] } },
  },
  layers: [
    { id: 'ground', type: 'background', paint: { 'background-color': MAP_COLORS.ground } },
    {
      id: 'green',
      type: 'fill',
      source: 'openmaptiles',
      'source-layer': 'landcover',
      filter: ['in', ['get', 'class'], ['literal', ['wood', 'grass']]],
      paint: { 'fill-color': MAP_COLORS.park },
    },
    {
      id: 'park',
      type: 'fill',
      source: 'openmaptiles',
      'source-layer': 'park',
      paint: { 'fill-color': MAP_COLORS.park },
    },
    {
      id: 'water',
      type: 'fill',
      source: 'openmaptiles',
      'source-layer': 'water',
      paint: { 'fill-color': MAP_COLORS.water },
    },
    {
      id: 'waterway',
      type: 'line',
      source: 'openmaptiles',
      'source-layer': 'waterway',
      paint: { 'line-color': MAP_COLORS.water, 'line-width': roadWidth(2) },
    },
    {
      id: 'building',
      type: 'fill',
      source: 'openmaptiles',
      'source-layer': 'building',
      minzoom: 14,
      paint: { 'fill-color': MAP_COLORS.building },
    },
    {
      id: 'roads-minor',
      type: 'line',
      source: 'openmaptiles',
      'source-layer': 'transportation',
      filter: ['in', ['get', 'class'], ['literal', MINOR_ROADS]],
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': MAP_COLORS.road, 'line-width': roadWidth(2) },
    },
    {
      id: 'roads-major',
      type: 'line',
      source: 'openmaptiles',
      'source-layer': 'transportation',
      filter: ['in', ['get', 'class'], ['literal', MAJOR_ROADS]],
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': MAP_COLORS.road, 'line-width': roadWidth(4) },
    },
    {
      id: 'route',
      type: 'line',
      source: 'route',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': MAP_COLORS.route, 'line-width': 4, 'line-dasharray': [0.1, 2] },
    },
  ],
};
