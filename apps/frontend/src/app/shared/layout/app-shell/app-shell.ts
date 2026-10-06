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
  styleUrl: './app-shell.css',
})
export class AppShell { readonly auth = inject(Auth); }
