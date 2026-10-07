import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ANALYST_REPORTS } from './reports/analyst-reports';

/**
 * Landing page of the ANALYST role: the list of app-wide aggregate reports. Analysts never see
 * an individual client, so nothing on this page or the reports may identify one.
 */
@Component({
  selector: 'staff-analyst-dashboard',
  imports: [RouterLink],
  template: `
    <h1>Analyst dashboard</h1>
    <p class="staff-placeholder">Placeholder. App-wide aggregate reports only.</p>

    <h2>Reports</h2>
    <ul>
      @for (report of reports; track report.path) {
        <li><a [routerLink]="['/analyst/reports', report.path]">{{ report.title }}</a></li>
      }
    </ul>
  `,
})
export class AnalystDashboard {
  protected readonly reports = ANALYST_REPORTS;
}
