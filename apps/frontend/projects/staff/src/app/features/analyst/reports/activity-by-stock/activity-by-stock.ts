import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Analyst report "Activity by stock". Placeholder: the report screen is built here. */
@Component({
  selector: 'staff-activity-by-stock',
  imports: [RouterLink],
  template: `
    <h1>Activity by stock</h1>
    <p class="staff-placeholder">Placeholder. This report is not built yet.</p>
    <a routerLink="/analyst">Back to reports</a>
  `,
})
export class ActivityByStock {}
