import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, of, switchMap, tap } from 'rxjs';
import { PriceHistory } from '../../../core/history/price-history';
import { InstrumentCatalog } from '../../../core/market/instrument-catalog';
import { PriceChart } from '../../../shared/components/price-chart/price-chart';
import { ChartPoint } from '../../../shared/models/chart.model';
import { SimulationSummary } from './simulation-summary';
import { chartRangeFor, dayOf, monthsBefore, simulate } from './simulator-math';

/** How far back the quick picks go, in months; the history covers six. */
const QUICK_PICKS = [
  { months: 1, label: '1 month ago' },
  { months: 3, label: '3 months ago' },
  { months: 6, label: '6 months ago' },
] as const;

const DEFAULT_AMOUNT = 1000;
const DEFAULT_MONTHS_BACK = 3;

/**
 * The Learn tab's stock simulator (`/learn/simulator?symbol=AAPL`): pick a stock, a day in the past
 * and a dollar amount, and see what that purchase would be worth now. It places no order and
 * touches no balance: it reads the stock's six-month daily history (the one the trade page's chart
 * uses, ending with the live price) and the arithmetic lives in simulator-math.ts.
 *
 * This component only holds the inputs, loads the prices and lays the result out.
 */
@Component({
  selector: 'app-simulator-page',
  imports: [RouterLink, PriceChart, SimulationSummary],
  templateUrl: './simulator-page.html',
  styleUrl: './simulator-page.css',
})
export class SimulatorPage {
  private readonly catalog = inject(InstrumentCatalog);
  private readonly history = inject(PriceHistory);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly quickPicks = QUICK_PICKS;

  protected readonly stocks = computed(() => this.catalog.all());
  /** The symbol asked for in the URL, upper-cased; empty until the route is read. */
  private readonly requested = signal('');
  protected readonly stock = computed(
    () => this.catalog.bySymbol(this.requested()) ?? this.stocks()[0] ?? null,
  );

  /** The stock's daily prices, oldest first, ending with the live price. */
  private readonly points = signal<ChartPoint[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly reload = signal(0);

  /** The day the user picked; empty until they pick one, when a default is used. */
  private readonly pickedDay = signal('');
  protected readonly amount = signal(DEFAULT_AMOUNT);

  /** The first and last day the loaded prices cover, or '' before they load. */
  protected readonly firstDay = computed(() => this.dayAt(0));
  protected readonly lastDay = computed(() => this.dayAt(this.points().length - 1));

  /** The day shown in the date box: the pick (kept inside the data) or three months back. */
  protected readonly day = computed(() => {
    const first = this.firstDay();
    const last = this.lastDay();
    if (!first) {
      return '';
    }
    const wanted = this.pickedDay() || monthsBefore(last, DEFAULT_MONTHS_BACK);
    return wanted < first ? first : wanted > last ? last : wanted;
  });

  protected readonly simulation = computed(() => simulate(this.points(), this.day(), this.amount()));
  protected readonly chartRange = computed(() => chartRangeFor(this.simulation()?.daysHeld ?? 0));
  protected readonly amountValid = computed(() => Number.isFinite(this.amount()) && this.amount() > 0);

  /** Why the purchase is not on the exact day shown, when it is not. */
  protected readonly dayNote = computed(() => {
    const sim = this.simulation();
    if (!sim || sim.buyDate === this.day()) {
      return null;
    }
    return `There was no trading on ${this.day()}, so the next trading day, ${sim.buyDate}, is used.`;
  });

  constructor() {
    this.catalog.load().catch(() => undefined);
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => this.requested.set((params.get('symbol') ?? '').trim().toUpperCase()));

    const request = computed(() => ({ symbol: this.stock()?.symbol ?? '', attempt: this.reload() }));
    toObservable(request)
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
          this.points.set([]);
        }),
        // switchMap drops a slower, older answer when the stock changes again.
        switchMap(({ symbol }) =>
          symbol
            ? this.history.stock(symbol, '6M').pipe(
                catchError((err: unknown) => {
                  this.error.set(
                    err instanceof HttpErrorResponse && err.status === 503
                      ? 'Prices are temporarily unavailable. Try again in a moment.'
                      : 'Something went wrong loading prices. Try again in a moment.',
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
          this.loading.set(false);
        }
      });
  }

  protected chooseStock(symbol: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { symbol }, replaceUrl: true });
  }

  protected onDay(value: string): void {
    this.pickedDay.set(value);
  }

  protected onAmount(value: string): void {
    // An empty box is NaN rather than 0, so it reads as "not entered" and not as "nothing".
    this.amount.set(value.trim() === '' ? NaN : Number(value));
  }

  protected pickMonthsAgo(months: number): void {
    this.pickedDay.set(monthsBefore(this.lastDay(), months));
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  private dayAt(index: number): string {
    const point = this.points().at(index);
    return point ? dayOf(point.time) : '';
  }
}
