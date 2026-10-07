import { Routes } from '@angular/router';
import { StaffShell } from './shared/layout/staff-shell/staff-shell';
import { TradingOpsDashboard } from './features/trading-ops/trading-ops-dashboard';
import { AnalystDashboard } from './features/analyst/analyst-dashboard';
import { ANALYST_REPORTS } from './features/analyst/reports/analyst-reports';
import { tradingOpsGuard } from './core/auth/trading-ops.guard';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login/login').then(m => m.Login) },
  { path: 'trade-search', title: 'Trade search', canActivate: [authGuard, tradingOpsGuard], loadComponent: () => import('./features/trade-search/trade-search').then(m => m.TradeSearch) },
  {
    path: '',
    component: StaffShell,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'trading-ops', pathMatch: 'full' },
      { path: 'trading-ops', title: 'Trading Ops', canActivate: [tradingOpsGuard], component: TradingOpsDashboard },
      { path: 'access-denied', loadComponent: () => import('./features/trade-search/access-denied').then(m => m.AccessDenied) },
      { path: 'analyst', title: 'Analyst', component: AnalystDashboard },
      ...ANALYST_REPORTS.map(({ path, title, loadComponent }) => ({
        path: `analyst/reports/${path}`,
        title,
        loadComponent,
      })),
    ],
  },
];
