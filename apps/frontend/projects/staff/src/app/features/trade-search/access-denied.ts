import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  template: `<h1>Access denied</h1><p>Trade search is available only to Trading Operations.</p>
    <a routerLink="/">Return home</a>`,
})
export class AccessDenied {}
