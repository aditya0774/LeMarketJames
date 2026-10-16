import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { ANALYST_REPORTS, VOLUME_TAB } from './reports/analyst-reports';

/**
 * The analyst workspace from the redesign's Analyst board: the open report's heading and one-line
 * description, the tab bar (Volume, then every report), and the report itself below.
 * Analysts never see individual clients, so nothing here or in the reports may identify one.
 */
@Component({
  selector: 'staff-analyst-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="analyst-head">
      <div class="analyst-title">
        <h1>{{ current().title }}</h1>
        <p>{{ current().subtitle }}</p>
      </div>
      <nav class="seg tabs" aria-label="Analyst reports">
        <a routerLink="/analyst" routerLinkActive="active" ariaCurrentWhenActive="page" [routerLinkActiveOptions]="{ exact: true }">
          {{ volume.tab }}
        </a>
        @for (report of reports; track report.path) {
          <a [routerLink]="['/analyst/reports', report.path]" routerLinkActive="active" ariaCurrentWhenActive="page">{{ report.tab }}</a>
        }
      </nav>
    </div>
    <router-outlet />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .analyst-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      flex-wrap: wrap;
      gap: 16px;
    }
    .analyst-title {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .analyst-title p {
      margin: 0;
      font-size: var(--font-base);
      color: var(--dim);
    }
    .tabs {
      flex-wrap: wrap;
    }
  `,
})
export class AnalystLayout {
  private readonly router = inject(Router);

  protected readonly reports = ANALYST_REPORTS;
  protected readonly volume = VOLUME_TAB;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** The heading for the open tab: the report whose path is in the URL, else Volume. */
  protected readonly current = computed(() => {
    const path = this.url().split('?')[0].split('/reports/')[1];
    return this.reports.find((report) => report.path === path) ?? this.volume;
  });
}
