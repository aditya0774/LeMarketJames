import { Routes } from '@angular/router';
import { Register } from './features/auth/register/register';
import { Login } from './features/auth/login/login';
import { HoldingsListComponent } from './features/holdings/holdings-list/holdings-list.component';

export const routes: Routes = [
  { path: 'register', component: Register },
  { path: 'login', component: Login },
  { path: 'holdings', component: HoldingsListComponent }
];