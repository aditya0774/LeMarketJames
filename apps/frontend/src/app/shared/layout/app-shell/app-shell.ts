import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Signed-in layout: LeMarket branding above a centred content area. The trading app is
 * customer-only (LMKT-145), so the layout links nothing else; staff use the staff app.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShell {}
