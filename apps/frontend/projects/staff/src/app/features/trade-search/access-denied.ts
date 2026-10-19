import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** The "Access denied" card from the redesign's States board. */
@Component({
  imports: [RouterLink],
  // Where requiresRole sends any refused role, so the wording is not about one page.
  template: `
    <section class="card ad-card">
      <span class="ad-cap">Access denied</span>
      <h1>You don't have access to this page</h1>
      <p>Your role does not have access to this page.</p>
      <a routerLink="/" class="btn btn-outline">Return home</a>
    </section>
  `,
  styles: `
    .ad-card {
      max-width: 480px;
      margin: var(--space-3xl) auto;
      padding: 24px;
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 10px;
    }
    .ad-cap {
      font-size: var(--font-2xs);
      font-weight: 500;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--faint2);
    }
    .ad-card h1 {
      font-size: var(--font-lg);
    }
    .ad-card p {
      margin: 0 0 6px;
      font-size: 13.5px;
      line-height: 1.5;
      color: var(--dim);
    }
  `,
})
export class AccessDenied {}
