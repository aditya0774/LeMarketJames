import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Brand } from '../../shared/components/brand/brand';

/**
 * Home Component
 *
 * Public landing page shown at the root route. Purely presentational —
 * links to login/register are handled via routerLink, with no local state.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, Brand],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {}
