import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
/**
 * Home Component
 *
 * Public landing page shown at the root route. Purely presentational —
 * links to login/register are handled via routerLink, with no local state.
 */
export class Home {}
