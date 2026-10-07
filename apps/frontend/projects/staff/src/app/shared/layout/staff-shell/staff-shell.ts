import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Layout of every staff page: a fixed sidebar with one entry per staff role's section, and the
 * page beside it. Unlike the trading app's AppShell it has no client branding or trading links.
 */
@Component({
  selector: 'staff-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './staff-shell.html',
  styleUrl: './staff-shell.css',
})
export class StaffShell {}
