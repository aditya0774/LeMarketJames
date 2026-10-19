import { NgTemplateOutlet } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * The lemarket brand plus the "Courtside View" badge, which marks this as the internal staff app
 * so it is never mistaken for the client app. Without a link it renders as plain text (the login
 * page, where there is nowhere to go yet).
 */
@Component({
  selector: 'staff-brand',
  imports: [RouterLink, NgTemplateOutlet],
  template: `
    @if (link(); as route) {
      <a class="brand" [routerLink]="route" aria-label="Courtside View home">
        <ng-container [ngTemplateOutlet]="mark" />
      </a>
    } @else {
      <span class="brand"><ng-container [ngTemplateOutlet]="mark" /></span>
    }
    <ng-template #mark>
      <span class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke-width="2.2" stroke-linejoin="round">
          <path d="M4 18 5.5 8l4.5 4 2-6 2 6 4.5-4L20 18Z" />
        </svg>
      </span>
      <span class="brand-word">lemarket</span>
      <span class="badge">Courtside View</span>
    </ng-template>
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
  `,
})
export class Brand {
  readonly link = input<string | null>(null);
}
