import { Routes } from '@angular/router';
import { Register } from './features/auth/register/register';
import { Login } from './features/auth/login/login';
import { OrderFormComponent } from './features/orders/order-form/order-form';
import { Home } from './features/home/home';
import { Dashboard } from './features/dashboard/dashboard';
import { HoldingsListComponent } from './features/holdings/holdings-list/holdings-list.component';
import { AppShell } from './shared/layout/app-shell/app-shell';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', component: Home, pathMatch: 'full' },
  { path: 'register', component: Register },
  { path: 'login', component: Login },
  { path: 'orders', component: OrderFormComponent },
  { path: 'holdings', component: HoldingsListComponent },
  // Signed-in pages share the sidebar layout from the LeUI mockup.
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [{ path: 'dashboard', component: Dashboard }],
  },
];
