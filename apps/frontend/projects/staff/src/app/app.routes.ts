import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { StaffShell } from './shared/layout/staff-shell/staff-shell';
import { AnalystDashboard } from './features/analyst/analyst-dashboard';
import { AnalystLayout } from './features/analyst/analyst-layout';
import { ANALYST_REPORTS } from './features/analyst/reports/analyst-reports';
import { Auth } from './core/auth/auth';
import { authGuard } from './core/auth/auth.guard';
import { requiresRole } from './core/auth/role.guard';
import { homeFor } from './core/auth/staff-home';
import { EMPLOYEE_INFORMATION } from './features/employee-information/employee-information.route';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login/login').then(m => m.Login) },
  {
    path: '',
    component: StaffShell,
    canActivate: [authGuard],
    children: [
      // The start page: the staff login for a signed-out visitor, otherwise the role's dashboard.
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => {
          const auth = inject(Auth);
          return auth.currentUser() ? homeFor(auth.roles()) : '/login';
        },
      },
      // Each role's section is one parent route that carries the guard, so a page added as its
      // child is guarded without repeating it.
      {
        path: 'trade-search',
        canActivate: [requiresRole('TRADING_OPS')],
        children: [{ path: '', title: 'Trade search', loadComponent: () => import('./features/trade-search/trade-search').then(m => m.TradeSearch) }],
      },
      // Surveillance alerts (BR-17); linked from the Trading Ops tab bar (trading-ops-nav.ts).
      {
        path: 'alerts',
        canActivate: [requiresRole('TRADING_OPS')],
        children: [{ path: '', title: 'Alerts', loadComponent: () => import('./features/trading-ops/alerts/alerts').then(m => m.Alerts) }],
      },
      {
        path: 'analyst',
        canActivate: [requiresRole('ANALYST')],
        // The layout carries the heading and the tab bar shared by the Volume page and every report.
        component: AnalystLayout,
        children: [
          { path: '', title: 'Analyst', component: AnalystDashboard },
          ...ANALYST_REPORTS.map(({ path, title, loadComponent }) => ({
            path: `reports/${path}`,
            title,
            loadComponent,
          })),
        ],
      },
      // Open to every signed-in staff member, whatever the role: each sees their own details (LMKT-144).
      {
        path: EMPLOYEE_INFORMATION.path.slice(1),
        title: EMPLOYEE_INFORMATION.title,
        loadComponent: () => import('./features/employee-information/employee-information').then(m => m.EmployeeInformation),
      },
      // Open to every signed-in account: it is where a refused role is sent.
      { path: 'access-denied', loadComponent: () => import('./features/trade-search/access-denied').then(m => m.AccessDenied) },
    ],
  },
  // An address that isn't a page goes to the start page instead of failing.
  { path: '**', redirectTo: '' },
];
