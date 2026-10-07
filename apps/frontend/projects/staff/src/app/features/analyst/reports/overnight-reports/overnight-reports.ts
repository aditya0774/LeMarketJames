import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Analyst report "Overnight reports". Placeholder: the report screen is built here. */
@Component({
  selector: 'staff-overnight-reports',
  imports: [RouterLink],
  template: `
    <h1>Overnight reports</h1>
    <p class="staff-placeholder">Placeholder. This report is not built yet.</p>
    <a routerLink="/analyst">Back to reports</a>
  `,
})
export class OvernightReports {}
