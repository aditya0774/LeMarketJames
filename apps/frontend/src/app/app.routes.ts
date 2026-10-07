import { Routes } from '@angular/router';
import { Register } from './features/auth/register/register';
import { Login } from './features/auth/login/login';
import { Home } from './features/home/home';
import { Dashboard } from './features/dashboard/dashboard';
import { HoldingsListComponent } from './features/holdings/holdings-list/holdings-list.component';
import { ByStockDetailComponent } from './features/holdings/by-stock-report/by-stock-detail.component';
import { AppShell } from './shared/layout/app-shell/app-shell';
import { authGuard } from './core/auth/auth.guard';
import { clientGuard } from './core/auth/client.guard';

export const routes: Routes = [
  { path: '', component: Home, pathMatch: 'full' },
  { path: 'register', component: Register },
  { path: 'login', component: Login },
  // Keep existing bookmarks on the supported, authenticated trading flow.
  { path: 'orders', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'holdings', component: HoldingsListComponent, canActivate: [clientGuard] },
  { path: 'holdings/:symbol', component: ByStockDetailComponent, canActivate: [clientGuard] },
  // Signed-in pages share the sidebar layout from the LeUI mockup.
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: Dashboard, canActivate: [clientGuard] },
      { path: 'access-denied', loadComponent: () => import('./features/trade-search/access-denied').then(m => m.AccessDenied) },
    ],
  },
];
