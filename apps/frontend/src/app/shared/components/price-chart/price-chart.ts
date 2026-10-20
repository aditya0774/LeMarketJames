import { Component, computed, input, output, signal } from '@angular/core';
import { CHART_RANGES, CHART_RANGE_LABELS, ChartPoint, ChartRange } from '../../models/chart.model';
import { changeText, chartGeometry, fullTime, money } from './chart-geometry';

/** What an empty chart says unless the page has something more specific. */
export const DEFAULT_EMPTY_TEXT = 'No prices to show for this range yet.';

/**
 * The redesign's value chart (Overview board): a title with the latest value and its change over
 * the range, a 1D–6M switch, and a line over a shaded area with round-number grid lines. Hovering or
 * arrowing across the plot shows each point's value, time and change since the range began.
 *
 * Presentational: the page loads the points for the chosen range and passes them in, so the same
 * chart draws the portfolio on the Overview and a stock on the trade page.
 */
@Component({
  selector: 'app-price-chart',
  templateUrl: './price-chart.html',
  styleUrl: './price-chart.css',
})
export class PriceChart {
  readonly points = input<readonly ChartPoint[]>([]);
  readonly range = input<ChartRange>('1M');
  readonly heading = input('');
  /** Whether to show the latest value under the title; the trade page already shows the price. */
  readonly showValue = input(true);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  /** What the empty chart says, e.g. that the portfolio fills in after a first purchase. */
  readonly emptyText = input(DEFAULT_EMPTY_TEXT);
  /** Whether to offer the 1D–6M switch; a page that fixes the span itself hides it. */
  readonly showRanges = input(true);
  /** Words for the span in the change line, e.g. "since you bought"; the range's own wording when null. */
  readonly spanLabel = input<string | null>(null);
  readonly rangeChange = output<ChartRange>();

  protected readonly ranges = CHART_RANGES;
  protected readonly rangeLabel = computed(() => this.spanLabel() ?? CHART_RANGE_LABELS[this.range()]);
  protected readonly geometry = computed(() => chartGeometry(this.points(), this.range()));
  protected readonly money = money;

  private readonly first = computed(() => this.points()[0]?.value ?? 0);
  protected readonly latest = computed(() => this.points().at(-1)?.value ?? null);
  protected readonly change = computed(() => (this.latest() ?? 0) - this.first());
  protected readonly changeLine = computed(() => `${changeText(this.first(), this.latest() ?? 0)} ${this.rangeLabel()}`);
  protected readonly summary = computed(() => {
    const latest = this.latest();
    return latest === null
      ? `${this.heading()}: no data`
      : `${this.heading()} ${this.rangeLabel()}, from ${money(this.first())} to ${money(latest)}`;
  });

  /** The point under the pointer or chosen with the arrow keys; null when none is. */
  protected readonly hovered = signal<number | null>(null);
  protected readonly hover = computed(() => {
    const index = this.hovered();
    const geometry = this.geometry();
    const point = index === null ? undefined : this.points()[index];
    if (!point || !geometry) {
      return null;
    }
    const x = this.points().length === 1 ? 50 : geometry.x(index!);
    return {
      x,
      y: geometry.y(point.value),
      // Over a point in the top half the tooltip sits at the bottom, so it never hides the point.
      low: geometry.y(point.value) < 50,
      value: money(point.value),
      time: fullTime(point.time, this.range()),
      up: point.value >= this.first(),
      change: `${changeText(this.first(), point.value)} since ${fullTime(this.points()[0].time, this.range())}`,
    };
  });

  protected chooseRange(range: ChartRange): void {
    this.hovered.set(null);
    this.rangeChange.emit(range);
  }

  // track and step are only wired up while there is a plot, so there is always at least one point.
  protected track(event: MouseEvent): void {
    const count = this.points().length;
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (event.clientX - box.left) / box.width));
    this.hovered.set(Math.round(fraction * (count - 1)));
  }

  /** Arrow keys step through the points, Home/End jump to the ends and Escape hides the tooltip. */
  protected step(event: KeyboardEvent): void {
    const last = this.points().length - 1;
    const current = this.hovered();
    const moves: Record<string, number | null> = {
      ArrowLeft: current === null ? last : Math.max(0, current - 1),
      ArrowRight: current === null ? last : Math.min(last, current + 1),
      Home: 0,
      End: last,
      Escape: null,
    };
    if (event.key in moves) {
      event.preventDefault();
      this.hovered.set(moves[event.key]);
    }
  }

  protected clear(): void {
    this.hovered.set(null);
  }
}
