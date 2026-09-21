import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Signed-in layout: LeMarket branding above a centred content area. Everything happens
 * on the dashboard (the buy/sell menu is a popup), so there is no side navigation.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet],
  templateUrl: './app-shell.html',
})
export class AppShell {}
