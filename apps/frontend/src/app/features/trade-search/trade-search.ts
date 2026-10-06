import { Component, inject, OnDestroy, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { TradeSearchQuery, TradeSearchResult, TradeSearchService } from '../../core/trades/trade-search.service';

@Component({
  selector: 'app-trade-search',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './trade-search.html',
  styleUrl: './trade-search.css',
})
export class TradeSearch implements OnDestroy {
  private readonly api = inject(TradeSearchService);
  private request?: Subscription;
  mode: 'order' | 'client' = 'order';
  orderId = '';
  clientId = '';
  from = '';
  to = '';
  readonly results = signal<TradeSearchResult[]>([]);
  readonly loading = signal(false);
  readonly searched = signal(false);
  readonly error = signal('');

  // Cancel old requests when criteria change so stale results cannot replace the current search.
  clearResults(): void {
    this.request?.unsubscribe();
    this.loading.set(false);
    this.searched.set(false);
    this.results.set([]);
    this.error.set('');
  }

  search(): void {
    this.clearResults();
    const id = this.mode === 'order' ? this.orderId : this.clientId;
    if (!/^[0-9]+$/.test(id) || Number(id) < 1 || Number(id) > 2147483647) {
      this.error.set(`Enter a valid positive ${this.mode === 'order' ? 'order' : 'client'} ID.`);
      return;
    }
    let query: TradeSearchQuery = { orderId: Number(id) };
    if (this.mode === 'client') {
      if (!this.validDate(this.from) || !this.validDate(this.to)) {
        this.error.set('Enter both dates as valid YYYY-MM-DD dates.');
        return;
      }
      if (this.to < this.from) {
        this.error.set('End date must be on or after start date.');
        return;
      }
      query = { clientId: Number(id), from: this.from, to: this.to };
    }
    this.loading.set(true);
    this.request = this.api.search(query).subscribe({
      next: results => {
        this.results.set(results);
        this.searched.set(true);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(error.status === 403 ? 'Access denied. Trade search requires Trading Operations.'
          : error.status === 401 ? 'Your session has expired. Please sign in again.'
          : error.status === 400 ? 'Check your search criteria and try again.'
          : 'Unable to search trades. Please try again.');
      },
    });
  }

  private validDate(value: string): boolean {
    if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.startsWith('0000')) return false;
    const date = new Date(`${value}T00:00:00Z`);
    return !isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
  }

  // C6 timestamps contain UTC wall-clock values without an offset; display explicitly as UTC.
  utcTime(value: string): string { return value.replace('T', ' ') + ' UTC'; }
  ngOnDestroy(): void { this.request?.unsubscribe(); }
}
