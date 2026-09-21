import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { EMPTY, switchMap } from 'rxjs';
import { InstrumentCatalog } from '../../../core/market/instrument-catalog';
import { Quotes } from '../../../core/quotes/quotes';
import { Quote } from '../../../shared/models/quote.model';

/**
 * Stock search box with a live-priced dropdown (mockup `.search-wrap`). Picking a stock
 * opens its trade page. Quotes are only polled while the dropdown is open.
 */
@Component({
  selector: 'app-stock-search',
  imports: [CurrencyPipe],
  templateUrl: './stock-search.html',
})
export class StockSearch {
  private readonly catalog = inject(InstrumentCatalog);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly prices = signal<Record<string, Quote | null>>({});
  protected readonly results = computed(() => this.catalog.search(this.query()));

  constructor() {
    const quotes = inject(Quotes);
    const symbols = this.catalog.all().map((i) => i.symbol);
    toObservable(this.open)
      .pipe(
        switchMap((isOpen) => (isOpen ? quotes.watchQuotes(symbols) : EMPTY)),
        takeUntilDestroyed(),
      )
      .subscribe((prices) => this.prices.set(prices));
  }

  protected onInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
  }

  protected select(symbol: string): void {
    this.open.set(false);
    this.query.set('');
    this.router.navigate(['/trade', symbol]);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('keydown.escape')
  protected close(): void {
    this.open.set(false);
  }
}
