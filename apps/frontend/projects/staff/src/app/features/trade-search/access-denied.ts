import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  // Where requiresRole sends any refused role, so the wording is not about one page.
  template: `<h1>Access denied</h1><p>Your role does not have access to this page.</p>
    <a routerLink="/">Return home</a>`,
})
export class AccessDenied {}
