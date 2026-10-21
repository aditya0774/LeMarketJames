import { Component, inject } from '@angular/core';
import { InsightFlagChip } from './insight-flag-chip';
import { changeText, InsightFlags } from './insight-flags';

/**
 * The top of the analyst dashboard (BR-17): every insight the tiles below have loaded, flagged
 * ones first, so a move of the configured percent or more is the first thing an analyst sees.
 */
@Component({
  selector: 'staff-what-changed',
  imports: [InsightFlagChip],
  template: `
    <section class="card what-changed" aria-labelledby="what-changed-title">
      <div class="head">
        <h2 id="what-changed-title">What changed</h2>
        @if (flags.thresholdPercent(); as threshold) {
          <p class="report-note">Flagged when an insight moves by {{ threshold }}% or more against the period before.</p>
        }
      </div>
      @if (!flags.loaded()) {
        <p class="status">Loading…</p>
      } @else {
        @if (flaggedCount() === 0) {
          <p class="status calm">Nothing moved by {{ flags.thresholdPercent() }}% or more.</p>
        }
        <ul>
          @for (insight of flags.all(); track insight.key) {
            <li [class.flagged]="insight.flag.flagged" [attr.data-key]="insight.key">
              @if (insight.flag.flagged) {
                <staff-insight-flag [flag]="insight.flag" />
              } @else {
                <span class="mono change">{{ changeText(insight.flag) }}</span>
              }
              <span class="label">{{ insight.label }}</span>
              <span class="compared mono">{{ insight.comparedWith }}</span>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .what-changed {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 18px;
    }
    .head {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    h2,
    .head p,
    .status {
      margin: 0;
    }
    .status {
      color: var(--dim);
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
      color: var(--dim);
    }
    li.flagged {
      color: inherit;
      font-weight: 500;
    }
    .change {
      min-width: 9ch;
    }
    .compared {
      font-size: var(--font-xs);
      color: var(--dim);
    }
  `,
})
export class WhatChanged {
  protected readonly flags = inject(InsightFlags);
  protected readonly changeText = changeText;

  protected flaggedCount(): number {
    return this.flags.all().filter((insight) => insight.flag.flagged).length;
  }
}
