import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { interval } from 'rxjs';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { ReportsTimeZoneService } from '../../../shared/services/reports-time-zone.service';
import { VolumeInsightService } from '../../../shared/services/volume-insight.service';
import { VolumeComparisonResponse, VolumeRangeRequest } from '../../../shared/models/volume-insight.model';
import { toIsoDay, volumePresets } from '../../../shared/utils/volume-ranges';
import { InsightFlags } from '../insight-flags/insight-flags';
import { InsightFlagChip } from '../insight-flags/insight-flag-chip';

type Direction = 'up' | 'down' | 'flat';

const CUSTOM = 'CUSTOM';

/** One comparison on screen: what was asked, and where the request stands. */
interface Card {
  key: string;
  label: string;
  request: VolumeRangeRequest;
  loading: boolean;
  error: boolean;
  data: VolumeComparisonResponse | null;
}

/** How often the tile reloads by itself, so a trade made now shows up without pressing Refresh. */
export const AUTO_REFRESH_MS = 30_000;

/** One day, or the first and last day of a range. */
function range(first: string, last: string): string {
  return first === last ? first : `${first} – ${last}`;
}

function newCard(key: string, label: string, request: VolumeRangeRequest): Card {
  return { key, label, request, loading: true, error: false, data: null };
}

@Component({
  selector: 'staff-volume-insight-tile',
  imports: [
    DatePipe, DecimalPipe, NgTemplateOutlet, ReactiveFormsModule,
    MatDatepickerModule, MatFormFieldModule,
    MatInputModule, MatNativeDateModule, InsightFlagChip,
  ],
  templateUrl: './volume-insight-tile.html',
  styleUrl: './volume-insight-tile.css',
})
export class VolumeInsightTile {
  private readonly insights = inject(VolumeInsightService);
  private readonly destroyRef = inject(DestroyRef);
  /** Where the preset cards' flags go for the "What changed" strip; absent outside the dashboard. */
  private readonly flags = inject(InsightFlags, { optional: true });

  /**
   * The reports time zone, whose day "today" is. Until it is known, or if it cannot be read, the
   * browser's day is used.
   */
  private timeZone: string | undefined;

  /** Today, this week so far and last week, each against the period before it. */
  readonly presets = signal<Card[]>(
    volumePresets(new Date()).map((p) => newCard(p.key, p.label, p.request)),
  );

  /** The comparison the user picked; null until they press Compare. */
  readonly custom = signal<Card | null>(null);

  /** Two ranges to compare, starting as this week against the same days of last week. */
  readonly pick = new FormGroup({
    currentFrom: new FormControl<Date | null>(null),
    currentTo: new FormControl<Date | null>(null),
    previousFrom: new FormControl<Date | null>(null),
    previousTo: new FormControl<Date | null>(null),
  });

  constructor() {
    this.fillPickFromThisWeek();
    // The days to ask for depend on the reports time zone, so the cards load once it is known.
    inject(ReportsTimeZoneService).timeZone$.pipe(takeUntilDestroyed()).subscribe({
      next: (zone) => {
        this.timeZone = zone;
        this.start();
      },
      error: () => this.start(),
    });
  }

  /** First load, then background reloads, which keep the figures on screen instead of flashing "Loading…". */
  private start(): void {
    this.refresh();
    this.fillPickFromThisWeek();
    interval(AUTO_REFRESH_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refresh(true));
  }

  /** Reloads every card. A silent reload keeps what is on screen, including after a failure. */
  refresh(silent = false): void {
    // Dates are worked out again so a tile left open overnight rolls over to the new day.
    const fresh = volumePresets(new Date(), this.timeZone);
    this.presets.update((all) => all.map((c, i) => ({ ...c, request: fresh[i].request })));
    for (const card of this.presets()) {
      this.load(card.key, silent);
    }
    if (this.custom()?.data || this.custom()?.loading) {
      this.load(CUSTOM, silent);
    }
  }

  /** Compares the two ranges in the form. All four days are needed. */
  compare(): void {
    const v = this.pick.getRawValue();
    if (!v.currentFrom || !v.currentTo || !v.previousFrom || !v.previousTo) {
      const none: VolumeRangeRequest = { currentFrom: '', currentTo: '', previousFrom: '', previousTo: '' };
      this.custom.set({ ...newCard(CUSTOM, 'Custom', none), loading: false, error: true });
      return;
    }
    const request: VolumeRangeRequest = {
      currentFrom: toIsoDay(v.currentFrom),
      currentTo: toIsoDay(v.currentTo),
      previousFrom: toIsoDay(v.previousFrom),
      previousTo: toIsoDay(v.previousTo),
    };
    this.custom.set(newCard(CUSTOM, 'Custom', request));
    this.load(CUSTOM, false);
  }

  direction(change: number): Direction {
    return change > 0 ? 'up' : change < 0 ? 'down' : 'flat';
  }

  /** The arrow shown before a change; the signed number beside it says the same in words. */
  icon(change: number): string {
    return { up: '▲', down: '▼', flat: '–' }[this.direction(change)];
  }

  /** One period's volume as a share of the larger of the two periods, for its bar (0–100). */
  share(volume: number, other: number): number {
    const largest = Math.max(volume, other);
    return largest > 0 ? (volume / largest) * 100 : 0;
  }

  private load(key: string, silent: boolean): void {
    const card = key === CUSTOM ? this.custom() : this.presets().find((c) => c.key === key);
    if (!card) {
      return;
    }
    if (!silent) {
      this.patch(key, { loading: true, error: false });
    }
    this.insights.compare(card.request).subscribe({
      next: (data) => this.patch(key, { loading: false, error: false, data }),
      // A failed background reload keeps the last good figures; only a manual one shows the error.
      error: () => (silent ? undefined : this.patch(key, { loading: false, error: true, data: null })),
    });
  }

  private patch(key: string, change: Partial<Card>): void {
    if (key === CUSTOM) {
      this.custom.update((c) => (c ? { ...c, ...change } : c));
    } else {
      this.presets.update((all) => all.map((c) => (c.key === key ? { ...c, ...change } : c)));
      this.reportFlags();
    }
  }

  /** The presets that have figures, for the strip. The custom range is the analyst's own question, so it stays here. */
  private reportFlags(): void {
    this.flags?.report('volume', this.presets()
      .filter((card) => card.data)
      .map((card) => ({
        key: `volume-${card.key}`,
        label: `Shares traded · ${card.label}`,
        comparedWith: `vs ${range(card.data!.previous.startDate, card.data!.previous.endDate)}`,
        flag: card.data!.flag,
      })));
  }

  private fillPickFromThisWeek(): void {
    const thisWeek = this.presets().find((c) => c.key === 'THIS_WEEK')!.request;
    const asDate = (iso: string) => new Date(`${iso}T00:00:00`);
    this.pick.setValue({
      currentFrom: asDate(thisWeek.currentFrom),
      currentTo: asDate(thisWeek.currentTo),
      previousFrom: asDate(thisWeek.previousFrom),
      previousTo: asDate(thisWeek.previousTo),
    });
  }
}
