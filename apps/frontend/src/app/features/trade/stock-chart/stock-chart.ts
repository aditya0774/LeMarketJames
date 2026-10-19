import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, catchError, of, switchMap, tap } from 'rxjs';
import { PriceHistory } from '../../../core/history/price-history';
import { ChartPoint, ChartRange } from '../../../shared/models/chart.model';
import { Quote } from '../../../shared/models/quote.model';
import { DEFAULT_EMPTY_TEXT, PriceChart } from '../../../shared/components/price-chart/price-chart';

/**
 * Adds the live quote to a loaded series: on 1D as a new point once it is newer than the last one,
 * on longer ranges as today's close. The page polls the quote anyway, so the chart keeps up with
 * the price beside it without polling history too. Exported for unit testing.
 */
export function withLivePrice(points: readonly ChartPoint[], range: ChartRange, quote: Quote | null): ChartPoint[] {
  if (!quote || !points.length) {
    return [...points];
  }
  const last = points[points.length - 1];
  if (range !== '1D') {
    return [...points.slice(0, -1), { time: last.time, value: quote.price }];
  }
  const time = Date.parse(quote.lastUpdate);
  return Number.isFinite(time) && time > last.time ? [...points, { time, value: quote.price }] : [...points];
}

/**
 * The trade page's chart of one stock's price over the chosen range. While the page says live
 * prices are down (`pricesDown`), the chart waits under the page's own banner instead of showing a
 * second error, and loads again once they are back.
 */
@Component({
  selector: 'app-stock-chart',
  imports: [PriceChart],
  template: `
    <app-price-chart
      [heading]="symbol() + ' price'"
      [showValue]="false"
      [points]="shown()"
      [range]="range()"
      [loading]="loading()"
      [error]="error()"
      [emptyText]="pricesDown() ? 'The chart is back as soon as live prices are.' : defaultEmptyText"
      (rangeChange)="range.set($event)"
    />
  `,
  styles: `
    :host {
      display: block;
      --chart-height: 180px;
    }
  `,
})
export class StockChart {
  readonly symbol = input('');
  readonly quote = input<Quote | null>(null);
  readonly pricesDown = input(false);

  protected readonly defaultEmptyText = DEFAULT_EMPTY_TEXT;
  protected readonly range = signal<ChartRange>('1D');
  private readonly points = signal<ChartPoint[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly shown = computed(() => withLivePrice(this.points(), this.range(), this.quote()));

  constructor() {
    const history = inject(PriceHistory);
    const request = computed(() => ({ symbol: this.symbol(), range: this.range(), down: this.pricesDown() }));
    toObservable(request)
      .pipe(
        tap(({ down }) => {
          this.loading.set(!down);
          this.error.set(null);
          this.points.set([]);
        }),
        // switchMap drops a slower, older answer when the stock, range or feed changes again.
        switchMap(({ symbol, range, down }) =>
          symbol && !down
            ? history.stock(symbol, range).pipe(
                catchError((err: unknown) => {
                  this.error.set(
                    err instanceof HttpErrorResponse && err.status === 503
                      // Not the page banner's wording, so the two never read as one message twice.
                      ? 'The chart needs live prices and is back once they are.'
                      : 'Something went wrong. Try again in a moment.',
                  );
                  this.loading.set(false);
                  return of(null);
                }),
              )
            : EMPTY,
        ),
        takeUntilDestroyed(),
      )
      .subscribe((points) => {
        if (points) {
          this.points.set(points);
          this.error.set(null);
          this.loading.set(false);
        }
      });
  }
}
