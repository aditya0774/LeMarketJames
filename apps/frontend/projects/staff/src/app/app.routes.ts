import { Routes } from '@angular/router';
import { StaffShell } from './shared/layout/staff-shell/staff-shell';
import { TradingOpsDashboard } from './features/trading-ops/trading-ops-dashboard';
import { AnalystDashboard } from './features/analyst/analyst-dashboard';
import { ANALYST_REPORTS } from './features/analyst/reports/analyst-reports';
import { analystGuard } from './core/auth/analyst.guard';

// Placeholder routes with ANALYST role guard on analyst section.
export const routes: Routes = [
  {
    path: '',
    component: StaffShell,
    children: [
      // Until login decides where each role lands, the app opens on the first section.
      { path: '', redirectTo: 'trading-ops', pathMatch: 'full' },
      { path: 'trading-ops', title: 'Trading Ops', component: TradingOpsDashboard },
      {
        path: 'analyst',
        title: 'Analyst',
        component: AnalystDashboard,
        canActivate: [analystGuard],
        children: [
          ...ANALYST_REPORTS.map(({ path, title, loadComponent }) => ({
            path: `reports/${path}`,
            title,
            loadComponent,
            canActivate: [analystGuard],
          })),
        ],
      },
    ],
  },
];
