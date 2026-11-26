# UI and Service Contract: Restaurant Card Details

## Service contract

```ts
export interface RoutePreview {
  readonly modes: readonly { readonly mode: 'walk' | 'bike' | 'car' | 'transit'; readonly minutes: number }[];
}

export abstract class RoutePreviewService {
  /** Travel times to the restaurant. Emits once; may error (the preview then shows a message, to validate). */
  abstract preview(restaurant: Restaurant): Observable<RoutePreview>;
}
```

`app.config.ts` provides `{ provide: RoutePreviewService, useClass: StubRoutePreviewService }`; feature 006 replaces the class.

## Screen contract (deck screen, `/deck`)

| Element | Behavior |
|---------|----------|
| Logo (mark + "Nappe"), top-left | Link to `/` (home). No confirmation. |
| "↻" on the front | Shows the back |
| Card tap (front) | Shows the back, unless the pointer sequence was a drag |
| "↻" or tap on the back | Shows the front |
| Back, header | Name, cuisine, distance, price level, keywords |
| Back, "Horaires" | 7 rows, targeted day highlighted; hidden when no hours |
| Back, "Adresse" | Address + "Itinéraire" button |
| "Itinéraire" | Opens the route preview inside the card (travel time per available mode) |
| Preview, full-route button | Navigates to `/route/:id` of the restaurant |
| Back, "Menu" | Dishes (name, description, price); hidden when no menu |
| Back content taller than the card | Scrolls inside the card |
| Reaction buttons (✕ ♥ →) | Unchanged, usable on both faces |
| Swipe on the back | Disabled |

Texts are in `deck.texts.ts`; proposals still to validate are listed in `plan.md` (D-04-x).
