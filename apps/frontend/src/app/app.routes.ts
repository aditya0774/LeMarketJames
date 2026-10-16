import { Routes } from '@angular/router';
import { Register } from './features/auth/register/register';
import { Login } from './features/auth/login/login';
import { Home } from './features/home/home';
import { Dashboard } from './features/dashboard/dashboard';
import { MarketPage } from './features/market/market-page';
import { OrdersPage } from './features/orders/orders-page';
import { TradePage } from './features/trade/trade-page';
import { HoldingsListComponent } from './features/holdings/holdings-list/holdings-list.component';
import { ByStockDetailComponent } from './features/holdings/by-stock-report/by-stock-detail.component';
import { AppShell } from './shared/layout/app-shell/app-shell';
import { authGuard } from './core/auth/auth.guard';
import { clientGuard } from './core/auth/client.guard';

export const routes: Routes = [
  { path: '', component: Home, pathMatch: 'full' },
  { path: 'register', component: Register },
  { path: 'login', component: Login },
  // Signed-in pages share the header with the section tabs (Overview, Market, Holdings, Orders).
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: Dashboard, canActivate: [clientGuard] },
      { path: 'market', component: MarketPage, canActivate: [clientGuard] },
      { path: 'orders', component: OrdersPage, canActivate: [clientGuard] },
      { path: 'trade', component: TradePage, canActivate: [clientGuard] },
      { path: 'holdings', component: HoldingsListComponent, canActivate: [clientGuard] },
      { path: 'holdings/:symbol', component: ByStockDetailComponent, canActivate: [clientGuard] },
      { path: 'access-denied', loadComponent: () => import('./features/trade-search/access-denied').then(m => m.AccessDenied) },
    ],
  },
];
