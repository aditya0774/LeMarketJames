import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Auth } from './core/auth/auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('lemarket-ui');

  constructor(auth: Auth) {
    void auth.restoreSession();
  }
}
