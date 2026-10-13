import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

import { VolumeInsightService } from '../../../shared/services/volume-insight.service';
import { VolumeInsightPeriodType, VolumeInsightResponse } from '../../../shared/models/volume-insight.model';

type Direction = 'up' | 'down' | 'flat';

/** Per-section state, so one failing request never blanks the other section. */
interface Section {
  periodType: VolumeInsightPeriodType;
  /** C6 compares completed periods only, so DAY is yesterday and WEEK is last week. */
  label: string;
  loading: boolean;
  error: boolean;
  data: VolumeInsightResponse | null;
}

const SECTIONS: readonly { periodType: VolumeInsightPeriodType; label: string }[] = [
  { periodType: 'DAY', label: 'Yesterday' },
  { periodType: 'WEEK', label: 'Last week' },
];

/**
 * Analyst dashboard tile: traded share volume for the last completed day and week,
 * each compared with the period before it (LMKT-46). Fetching is delegated to
 * VolumeInsightService; this component only owns the display state.
 */
@Component({
  selector: 'staff-volume-insight-tile',
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './volume-insight-tile.html',
  styleUrls: ['./volume-insight-tile.scss'],
})
export class VolumeInsightTile {
  private readonly insights = inject(VolumeInsightService);

  readonly sections = signal<Section[]>(
    SECTIONS.map((s) => ({ ...s, loading: true, error: false, data: null })),
  );

  constructor() {
    this.refresh();
  }

  /** Re-fetch both periods; nothing is cached server-side, so this shows fresh figures. */
  refresh(): void {
    for (const s of SECTIONS) {
      this.update(s.periodType, { loading: true, error: false });
      this.insights.get(s.periodType).subscribe({
        next: (data) => this.update(s.periodType, { loading: false, data }),
        error: () => this.update(s.periodType, { loading: false, error: true, data: null }),
      });
    }
  }

  /** Up, down or flat, so the tile never relies on colour alone. */
  direction(change: number): Direction {
    return change > 0 ? 'up' : change < 0 ? 'down' : 'flat';
  }

  icon(change: number): string {
    return { up: 'arrow_upward', down: 'arrow_downward', flat: 'remove' }[this.direction(change)];
  }

  private update(periodType: VolumeInsightPeriodType, patch: Partial<Section>): void {
    this.sections.update((all) => all.map((s) => (s.periodType === periodType ? { ...s, ...patch } : s)));
  }
}
