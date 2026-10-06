import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Analyst report "Activity by client segment". Placeholder: the report screen is built here. */
@Component({
  selector: 'staff-activity-by-client-segment',
  imports: [RouterLink],
  template: `
    <h1>Activity by client segment</h1>
    <p class="staff-placeholder">Placeholder. This report is not built yet.</p>
    <a routerLink="/analyst">Back to reports</a>
  `,
})
export class ActivityByClientSegment {}
