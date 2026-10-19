import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * The lemarket brand: a crown mark in a primary-coloured square next to the lowercase wordmark.
 * Every page header uses it, so the logo is drawn in one place. It links home by default; pass
 * another route, e.g. the dashboard for signed-in pages.
 */
@Component({
  selector: 'app-brand',
  imports: [RouterLink],
  template: `
    <a class="brand" [class.lg]="size() === 'lg'" [routerLink]="link()" aria-label="lemarket home">
      <span class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke-width="2.2" stroke-linejoin="round">
          <path d="M4 18 5.5 8l4.5 4 2-6 2 6 4.5-4L20 18Z" />
        </svg>
      </span>
      <span class="brand-word">lemarket</span>
    </a>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: var(--text);
    }
    .brand:hover {
      color: var(--text);
    }
    .brand-mark {
      width: 28px;
      height: 28px;
      border-radius: var(--radius-sm);
      background: var(--pri);
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .brand-mark svg {
      width: 16px;
      height: 16px;
      stroke: var(--on-pri);
    }
    .brand-word {
      font-weight: 600;
      font-size: 16px;
    }
    .lg .brand-mark {
      width: 30px;
      height: 30px;
    }
    .lg .brand-mark svg {
      width: 18px;
      height: 18px;
    }
    .lg .brand-word {
      font-size: 17px;
      letter-spacing: -0.01em;
    }
  `,
})
export class Brand {
  readonly link = input<string>('/');
  readonly size = input<'md' | 'lg'>('md');
}
