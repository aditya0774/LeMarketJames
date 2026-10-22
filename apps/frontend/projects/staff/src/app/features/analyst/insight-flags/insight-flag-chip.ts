import { Component, computed, input } from '@angular/core';
import { InsightFlag } from '../../../shared/models/insight-flag.model';
import { changeText } from './insight-flags';

/**
 * The flag on an insight that moved by at least the configured percent (BR-17): an arrow, the
 * move, and "Flagged" in words, so it reads without colour. Shows nothing for an insight that is
 * not flagged.
 */
@Component({
  selector: 'staff-insight-flag',
  template: `
    @if (flag()?.flagged) {
      <span class="pill flag" [class.up]="flag()!.direction === 'UP'" [class.down]="flag()!.direction === 'DOWN'"
            [attr.title]="'Moved by ' + flag()!.thresholdPercent + '% or more against the period before'">
        <span aria-hidden="true">{{ flag()!.direction === 'DOWN' ? '▼' : '▲' }}</span>
        Flagged {{ text() }}
      </span>
    }
  `,
  styles: `
    .flag {
      white-space: nowrap;
      background: var(--pri-soft);
      color: var(--pritext);
    }
    .flag.up {
      background: var(--up-soft);
      color: var(--upt);
    }
    .flag.down {
      background: var(--down-soft);
      color: var(--errt);
    }
  `,
})
export class InsightFlagChip {
  readonly flag = input<InsightFlag | null | undefined>(null);

  protected readonly text = computed(() => {
    const flag = this.flag();
    return flag ? changeText(flag) : '';
  });
}
