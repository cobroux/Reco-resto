/**
 * Fake of `maplibre-gl` for the specs: jsdom has no WebGL, so the real map cannot start. It records what the
 * component asks of the map. Use with `vi.mock('maplibre-gl', async () => (await import(...)).maplibreMock)`.
 */
type Listener = () => void;

export const mapCalls = {
  maps: [] as FakeMap[],
  markers: [] as FakeMarker[],
  routes: [] as unknown[],
  fitBounds: [] as unknown[][],
  easeTo: [] as unknown[][],
  removedMaps: 0,
  /** When true, the map never fires 'load' by itself (tiles not loaded yet). */
  holdLoad: false,
  /** When true, creating a map throws (no WebGL). */
  failConstruct: false,
};

export function resetMapCalls(): void {
  mapCalls.maps = [];
  mapCalls.markers = [];
  mapCalls.routes = [];
  mapCalls.fitBounds = [];
  mapCalls.easeTo = [];
  mapCalls.removedMaps = 0;
  mapCalls.holdLoad = false;
  mapCalls.failConstruct = false;
}

class FakeMap {
  private readonly listeners = new Map<string, Listener[]>();
  constructor(readonly options: Record<string, unknown>) {
    if (mapCalls.failConstruct) {
      throw new Error('WebGL unavailable');
    }
    mapCalls.maps.push(this);
  }
  addControl(): void {}
  on(event: string, listener: Listener): void {
    this.listeners.set(event, [...(this.listeners.get(event) ?? []), listener]);
    if (event === 'load' && !mapCalls.holdLoad) {
      listener();
    }
  }
  /** Fires the listeners of `event`, as MapLibre does (e.g. 'error' when the tiles cannot be fetched). */
  emit(event: string): void {
    (this.listeners.get(event) ?? []).forEach((listener) => listener());
  }
  getSource(): { setData: (data: unknown) => void } {
    return { setData: (data) => mapCalls.routes.push(data) };
  }
  fitBounds(...args: unknown[]): void {
    mapCalls.fitBounds.push(args);
  }
  easeTo(...args: unknown[]): void {
    mapCalls.easeTo.push(args);
  }
  remove(): void {
    mapCalls.removedMaps++;
  }
}

class FakeMarker {
  lngLat: [number, number] | undefined;
  removed = false;
  readonly element: HTMLElement;
  constructor(options: { element: HTMLElement }) {
    this.element = options.element;
    mapCalls.markers.push(this);
  }
  setLngLat(lngLat: [number, number]): this {
    this.lngLat = lngLat;
    return this;
  }
  addTo(): this {
    return this;
  }
  remove(): this {
    this.removed = true;
    return this;
  }
}

class FakeLngLatBounds {
  readonly points: [number, number][];
  constructor(a: [number, number], b: [number, number]) {
    this.points = [a, b];
  }
  extend(point: [number, number]): this {
    this.points.push(point);
    return this;
  }
}

class FakeNavigationControl {}

const maplibre = {
  Map: FakeMap,
  Marker: FakeMarker,
  LngLatBounds: FakeLngLatBounds,
  NavigationControl: FakeNavigationControl,
};

export const maplibreMock = { default: maplibre, ...maplibre };
