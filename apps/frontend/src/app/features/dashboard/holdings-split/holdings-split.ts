import { Component, computed, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HoldingDto } from '../../../shared/models/holdings.model';
import { allocation } from '../portfolio-totals';
import { CompactNumberPipe } from '../../../shared/pipes/compact-number.pipe';

/** Chart series colours, in order; a sixth or later holding reuses them from the start. */
const SERIES = ['var(--d1)', 'var(--d2)', 'var(--d3)', 'var(--d4)', 'var(--d5)'];
/** The donut is drawn on a circle of circumference 100, so a percentage is a dash length. */
const RADIUS = 15.915;
/** A small gap between slices so neighbours stay distinct. */
const GAP = 0.6;

/**
 * "What you own" from the redesign's Overview: a donut of how the portfolio's value is split
 * across stocks, with each stock's share and gain. Selecting a stock asks the page to load it
 * into quick trade.
 */
@Component({
  selector: 'app-holdings-split',
  imports: [DecimalPipe, RouterLink, CompactNumberPipe],
  template: `
    <section class="split card" aria-labelledby="split-title">
      <div class="split-head">
        <h2 id="split-title">What you own</h2>
        <a class="btn-link" routerLink="/holdings">Details →</a>
      </div>
      @if (loading()) {
        <div class="loading" role="status"><span class="spinner"></span>Chalking up…</div>
      } @else if (error()) {
        <div class="alert err" role="alert"><div><b>Couldn't load your holdings</b>{{ error() }}</div></div>
      } @else if (!slices().length) {
        <div class="empty-state">
          <strong>No stocks yet</strong>
          Once you buy your first stock, it shows up here with what it's worth.
        </div>
      } @else {
        <div class="split-body">
          <svg class="donut" viewBox="0 0 42 42" role="img" [attr.aria-label]="summary()">
            <circle cx="21" cy="21" [attr.r]="radius" class="donut-track" />
            @for (s of arcs(); track s.symbol) {
              <circle cx="21" cy="21" [attr.r]="radius" class="donut-arc" [style.stroke]="s.color"
                      [attr.stroke-dasharray]="s.dash" [attr.stroke-dashoffset]="s.offset" />
            }
            <text x="21" y="20" text-anchor="middle" class="donut-sub">{{ slices().length }} stock{{ slices().length === 1 ? '' : 's' }}</text>
            <text x="21" y="25" text-anchor="middle" class="donut-val">\${{ totalValue() | compactNumber }}</text>
          </svg>
          <ul class="split-list">
            @for (s of arcs(); track s.symbol) {
              <li>
                <button type="button" [attr.aria-label]="'Load ' + s.symbol + ' into quick trade'" (click)="selectSymbol.emit(s.symbol)">
                  <i [style.background]="s.color"></i>
                  <span class="sym">{{ s.symbol }}</span>
                  <span class="mono muted pct">{{ s.percent | number: '1.0-0' }}%</span>
                  <span class="mono gain" [class.up]="s.gainLossPercent >= 0" [class.down]="s.gainLossPercent < 0">
                    {{ s.gainLossPercent >= 0 ? '+' : '−' }}{{ (s.gainLossPercent < 0 ? -s.gainLossPercent : s.gainLossPercent) | number: '1.1-1' }}%
                  </span>
                </button>
              </li>
            }
          </ul>
        </div>
      }
    </section>
  `,
  styleUrl: './holdings-split.css',
})
export class HoldingsSplit {
  readonly holdings = input<readonly HoldingDto[]>([]);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly selectSymbol = output<string>();

  protected readonly radius = RADIUS;
  protected readonly slices = computed(() => allocation(this.holdings()));
  protected readonly totalValue = computed(() => this.holdings().reduce((sum, h) => sum + h.currentValue, 0));

  /** Each slice as a dash on the circle, starting at 12 o'clock and going clockwise. */
  protected readonly arcs = computed(() => {
    let start = 0;
    return this.slices().map((slice, i) => {
      const length = Math.max(0, slice.percent - GAP);
      const arc = {
        ...slice,
        color: SERIES[i % SERIES.length],
        dash: `${length.toFixed(2)} ${(100 - length).toFixed(2)}`,
        // The circle starts at 3 o'clock; an offset of 25 moves the first slice to 12.
        offset: (25 - start).toFixed(2),
      };
      start += slice.percent;
      return arc;
    });
  });

  protected readonly summary = computed(
    () => 'Portfolio split: ' + this.slices().map((s) => `${s.symbol} ${Math.round(s.percent)}%`).join(', '),
  );
}
