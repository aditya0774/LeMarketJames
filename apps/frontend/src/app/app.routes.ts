import { Routes } from '@angular/router';
import { Register } from './features/auth/register/register';
import { Login } from './features/auth/login/login';

export const routes: Routes = [
  { path: 'register', component: Register },
  { path: 'login', component: Login }
];