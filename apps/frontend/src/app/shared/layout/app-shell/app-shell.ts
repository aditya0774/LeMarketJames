import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Auth } from '../../../core/auth/auth';

/**
 * Signed-in layout: LeMarket branding above a centred content area. Everything happens
 * on the dashboard for clients; operations also have a trade-search entry point.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app-shell.html',
  styles: ['nav a { color: var(--gold); font-weight: 600; } nav { margin: 16px 0; }'],
})
export class AppShell { readonly auth = inject(Auth); }
