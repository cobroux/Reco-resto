import { Routes } from '@angular/router';
import { DeckComponent } from './deck/deck.component';
import { ResultsPlaceholderComponent } from './placeholders/results-placeholder.component';
import { RoutePlaceholderComponent } from './placeholders/route-placeholder.component';

export const routes: Routes = [
  { path: 'deck', component: DeckComponent },
  // Temporary screens, replaced by features 005 (results) and 006 (route).
  { path: 'results', component: ResultsPlaceholderComponent },
  { path: 'route/:id', component: RoutePlaceholderComponent },
];
