import { Routes } from '@angular/router';
import { Register } from './features/auth/register/register';
import { Login } from './features/auth/login/login';
import { OrderFormComponent } from './features/orders/order-form/order-form';
import { Home } from './features/home/home';
import { Dashboard } from './features/dashboard/dashboard';
import { HoldingsListComponent } from './features/holdings/holdings-list/holdings-list.component';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'register', component: Register },
  { path: 'login', component: Login },
  { path: 'orders', component: OrderFormComponent },
  { path: 'dashboard', component: Dashboard },
  { path: 'holdings', component: HoldingsListComponent }
];