import { Component } from '@angular/core';

/** Analyst report "Overnight reports". Placeholder until the report is built; wording from the redesign. */
@Component({
  selector: 'staff-overnight-reports',
  template: `
    <section class="card overnight">
      <svg viewBox="0 0 24 24" fill="none" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z" />
      </svg>
      <h2>Overnight reports are on the way</h2>
      <p>End-of-day and overnight trading summaries will live here. This report isn't built yet.</p>
    </section>
  `,
  styles: `
    .overnight {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 56px 40px;
      text-align: center;
    }
    svg {
      width: 36px;
      height: 36px;
      stroke: var(--acc);
    }
    h2 {
      font-size: var(--font-lg);
    }
    p {
      margin: 0;
      max-width: 420px;
      font-size: var(--font-base);
      line-height: 1.6;
      color: var(--dim);
    }
  `,
})
export class OvernightReports {}
