import { Routes } from '@angular/router';
import { DeckComponent } from './deck/deck.component';
import { searchResultGuard } from './deck/search-result.guard';
import { HomeComponent } from './home/home.component';
import { ChoiceComponent } from './placeholders/choice.component';
import { ResultsPlaceholderComponent } from './placeholders/results-placeholder.component';
import { RoutePlaceholderComponent } from './placeholders/route-placeholder.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  // The deck opens only after a search (spec 003 FR-021).
  { path: 'deck', component: DeckComponent, canActivate: [searchResultGuard] },
  // The restaurant chosen with "On y va".
  { path: 'choice/:id', component: ChoiceComponent },
  // Temporary screens, replaced by features 005 (results) and 006 (route).
  { path: 'results', component: ResultsPlaceholderComponent },
  { path: 'route/:id', component: RoutePlaceholderComponent },
];
