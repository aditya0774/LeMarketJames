import { Injectable, computed, signal } from '@angular/core';
import { InsightFlag } from '../../../shared/models/insight-flag.model';

/** One insight as the "What changed" strip lists it. */
export interface ChangedInsight {
  /** Unique on the dashboard, e.g. `volume-TODAY` or `stock-AAPL`. */
  key: string;
  /** What moved, e.g. "Volume · Today". */
  label: string;
  /** What it is compared with, e.g. "vs 2026-10-13". */
  comparedWith: string;
  flag: InsightFlag;
}

/**
 * The flags the dashboard's tiles have loaded, for the "What changed" strip (BR-17). Each tile
 * reports what it already fetched, so the strip makes no request of its own: reports share a
 * one-connection pool on the server (staff-ops README). Provided by the dashboard, so it lives
 * and dies with it; a tile used on its own has none and reports nothing.
 */
@Injectable()
export class InsightFlags {
  private readonly bySource = signal<ReadonlyMap<string, readonly ChangedInsight[]>>(new Map());

  /** Every insight reported, flagged ones first, then the largest moves. */
  readonly all = computed(() => [...this.bySource().values()].flat().sort(flaggedFirst));

  /** Whether any tile has reported yet. */
  readonly loaded = computed(() => this.bySource().size > 0);

  /** The setting in force, as the server sent it; null until a tile has reported. */
  readonly thresholdPercent = computed(() => this.all()[0]?.flag.thresholdPercent ?? null);

  /** Replaces what one tile reported before. */
  report(source: string, insights: readonly ChangedInsight[]): void {
    this.bySource.update((current) => new Map(current).set(source, insights));
  }
}

/** A move from nothing has no percent but is the largest there is. */
function size(flag: InsightFlag): number {
  return flag.changePercent === null ? (flag.direction === 'FLAT' ? 0 : Infinity) : Math.abs(flag.changePercent);
}

function flaggedFirst(a: ChangedInsight, b: ChangedInsight): number {
  return Number(b.flag.flagged) - Number(a.flag.flagged) || size(b.flag) - size(a.flag);
}

/** The move in words: "+30%", "−20.5%", "new" when there was none before, "no change". */
export function changeText(flag: InsightFlag): string {
  if (flag.changePercent === null) {
    return flag.direction === 'FLAT' ? 'no change' : 'new';
  }
  const amount = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(Math.abs(flag.changePercent));
  return flag.changePercent > 0 ? `+${amount}%` : flag.changePercent < 0 ? `−${amount}%` : '0%';
}
