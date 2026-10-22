import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { interval } from 'rxjs';

import { ActiveClientTrendService } from '../../../shared/services/active-client-trend.service';
import { ActiveClientTrendResponse } from '../../../shared/models/active-client-trend.model';
import { buildTrendChart, CHART_HEIGHT, CHART_WIDTH } from '../../../shared/utils/trend-chart';
import { AUTO_REFRESH_MS } from '../volume-insight-tile/volume-insight-tile';
import { InsightFlags } from '../insight-flags/insight-flags';
import { InsightFlagChip } from '../insight-flags/insight-flag-chip';

/** Insight tile: distinct clients who traded on each of the last 30 days (LMKT-48). */
@Component({
  selector: 'staff-active-client-trend-tile',
  imports: [DatePipe, DecimalPipe, InsightFlagChip],
  templateUrl: './active-client-trend-tile.html',
  styleUrl: './active-client-trend-tile.css',
})
export class ActiveClientTrendTile {
  private readonly trend = inject(ActiveClientTrendService);
  /** Where the trend's flag goes for the "What changed" strip; absent outside the dashboard. */
  private readonly flags = inject(InsightFlags, { optional: true });

  protected readonly chartWidth = CHART_WIDTH;
  protected readonly chartHeight = CHART_HEIGHT;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly data = signal<ActiveClientTrendResponse | null>(null);

  protected readonly chart = computed(() => buildTrendChart(this.data()?.days ?? []));
  /** The last day of the window is today, counted so far. */
  protected readonly latest = computed(() => this.data()?.days.at(-1)?.activeClients ?? 0);

  constructor() {
    this.refresh();
    // Background reloads keep the figures on screen instead of flashing "Loading…".
    interval(AUTO_REFRESH_MS).pipe(takeUntilDestroyed()).subscribe(() => this.refresh(true));
  }

  /** Reloads the trend. A silent reload keeps what is on screen, including after a failure. */
  refresh(silent = false): void {
    if (!silent) {
      this.loading.set(true);
      this.error.set(false);
    }
    this.trend.get().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
        this.error.set(false);
        this.flags?.report('active-clients', [{
          key: 'active-clients',
          label: 'Active clients, daily average',
          comparedWith: `${data.lastWeek.startDate} – ${data.lastWeek.endDate} vs ${data.weekBefore.startDate} – ${data.weekBefore.endDate}`,
          flag: data.flag,
        }]);
      },
      // A failed background reload keeps the last good figures; only a manual one shows the error.
      error: () => {
        if (!silent) {
          this.data.set(null);
          this.loading.set(false);
          this.error.set(true);
        }
      },
    });
  }
}
