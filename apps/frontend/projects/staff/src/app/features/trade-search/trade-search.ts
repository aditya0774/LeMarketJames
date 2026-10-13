import { Component, inject, OnDestroy, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ClientSearchResult, TradeSearchResult, TradeSearchService } from '../../core/trades/trade-search.service';

@Component({ selector: 'staff-trade-search', imports: [FormsModule, DecimalPipe],
  templateUrl: './trade-search.html', styleUrl: './trade-search.css' })
export class TradeSearch implements OnDestroy {
  private readonly api = inject(TradeSearchService);
  private request?: Subscription;
  mode: 'order' | 'client' = 'order';
  term = '';
  from = '';
  to = '';
  readonly clients = signal<ClientSearchResult[]>([]);
  readonly clientsSearched = signal(false);
  readonly results = signal<TradeSearchResult[]>([]);
  readonly loading = signal(false);
  readonly searched = signal(false);
  readonly error = signal('');
  readonly selected = signal('');

  clearResults(): void {
    this.request?.unsubscribe();
    this.loading.set(false); this.searched.set(false); this.results.set([]);
    this.error.set(''); this.clients.set([]); this.clientsSearched.set(false);
    this.selected.set('');
  }

  search(): void {
    this.clearResults();
    const term = this.term.trim();
    if (!term) { this.error.set('Enter an order ID, client ID or client name.'); return; }
    if ((this.from || this.to) && (!this.validDate(this.from) || !this.validDate(this.to))) {
      this.error.set('Enter both dates as valid YYYY-MM-DD dates.'); return;
    }
    if (this.to < this.from) { this.error.set('End date must be on or after start date.'); return; }
    if (/^[0-9]+$/.test(term)) {
      const id = Number(term);
      if (id < 1 || id > 2147483647) { this.error.set('Enter a valid positive ID.'); return; }
      this.loading.set(true);
      const query = this.mode === 'order' ? {orderId: id} : this.clientQuery(id);
      this.request = this.api.search(query).subscribe({next: trades => {
        this.show({label: `${this.mode === 'order' ? 'Order' : 'Client'} ID ${id}`, trades});
      }, error: error => this.failed(error)});
    } else {
      if (term.length < 2 || term.length > 100) { this.error.set('Enter a client name between 2 and 100 characters.'); return; }
      this.loading.set(true);
      this.request = this.api.clients(term).subscribe({next: clients => {
        this.clients.set(clients); this.clientsSearched.set(true); this.loading.set(false);
        if (clients.length === 1) this.selectClient(clients[0]);
      }, error: error => this.failed(error)});
    }
  }

  selectClient(client: ClientSearchResult): void {
    this.request?.unsubscribe(); this.error.set(''); this.loading.set(true);
    this.request = this.api.search(this.clientQuery(client.clientId)).subscribe({next: trades => {
      this.show({label: `${client.fullName} (Client ID ${client.clientId})`, trades});
    }, error: error => this.failed(error)});
  }

  show(choice: {label: string; trades: TradeSearchResult[]}): void {
    this.results.set(choice.trades); this.selected.set(choice.label);
    this.searched.set(true); this.loading.set(false);
  }

  private clientQuery(clientId: number) {
    return this.from ? {clientId, from: this.from, to: this.to} : {clientId};
  }
  private failed(error: HttpErrorResponse): void {
    this.loading.set(false);
    this.error.set(error.status === 403 ? 'Access denied. Trade search requires Trading Operations.'
      : error.status === 401 ? 'Your session has expired. Please sign in again.'
      : error.status === 400 ? 'Check your search criteria and try again.'
      : 'Unable to search trades. Please try again.');
  }
  private validDate(value: string): boolean {
    if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.startsWith('0000')) return false;
    const date = new Date(`${value}T00:00:00Z`);
    return !isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
  }
  utcTime(value: string): string { return value.replace('T', ' ') + ' UTC'; }
  ngOnDestroy(): void { this.request?.unsubscribe(); }
}
