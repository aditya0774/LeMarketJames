import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Auth } from '../auth/auth';
import { WatchlistResponse } from '../../shared/models/watchlist.model';

/**
 * Where the watchlist stands: loading, ready, `unavailable` while the back end does not exist yet
 * (its endpoint answers 404, C6 Planned), or `error` for any other failure.
 */
export type WatchlistStatus = 'loading' | 'ready' | 'unavailable' | 'error';

/**
 * The signed-in client's watchlist (S41): the stocks they keep an eye on, newest first. Adding and
 * removing show at once and are undone if the server refuses, so the star never waits on the
 * network. State lives here rather than in a page so the Market tab and any later screen agree.
 */
@Injectable({ providedIn: 'root' })
export class Watchlist {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/v1/watchlist`;

  private readonly list = signal<string[]>([]);
  readonly symbols = this.list.asReadonly();
  readonly watched = computed(() => new Set(this.list()));
  readonly status = signal<WatchlistStatus>('loading');
  /** The last add or remove that failed, in words; cleared by the next successful change. */
  readonly changeError = signal<string | null>(null);

  constructor() {
    // A watchlist belongs to one account and must never be shown to the next one to sign in.
    const auth = inject(Auth);
    let previous = auth.currentAccountId();
    effect(() => {
      const account = auth.currentAccountId();
      if (account !== previous) {
        previous = account;
        this.list.set([]);
        this.status.set('loading');
        this.changeError.set(null);
      }
    });
  }

  async load(): Promise<void> {
    this.status.set('loading');
    try {
      const response = await firstValueFrom(this.http.get<WatchlistResponse>(this.url));
      this.list.set((response.watchlist ?? []).map((item) => item.symbol));
      this.status.set('ready');
    } catch (err) {
      this.status.set(err instanceof HttpErrorResponse && err.status === 404 ? 'unavailable' : 'error');
    }
  }

  isWatched(symbol: string): boolean {
    return this.watched().has(symbol);
  }

  toggle(symbol: string): Promise<void> {
    return this.isWatched(symbol) ? this.remove(symbol) : this.add(symbol);
  }

  async add(symbol: string): Promise<void> {
    if (this.isWatched(symbol)) {
      return;
    }
    this.list.update((list) => [symbol, ...list]);
    try {
      await firstValueFrom(this.http.post(this.url, { symbol }));
      this.changeError.set(null);
    } catch (err) {
      // Already on the list server-side (another tab added it): the star is right as it is.
      if (err instanceof HttpErrorResponse && err.status === 409) {
        return;
      }
      this.list.update((list) => list.filter((s) => s !== symbol));
      this.changeError.set(`Couldn't add ${symbol} to your watchlist. Try again in a moment.`);
    }
  }

  async remove(symbol: string): Promise<void> {
    const before = this.list();
    if (!before.includes(symbol)) {
      return;
    }
    this.list.set(before.filter((s) => s !== symbol));
    try {
      await firstValueFrom(this.http.delete(`${this.url}/${encodeURIComponent(symbol)}`));
      this.changeError.set(null);
    } catch {
      // Put it back where it was, so the list order survives the failed attempt.
      this.list.update((list) => (list.includes(symbol) ? list : before.filter((s) => s === symbol || list.includes(s))));
      this.changeError.set(`Couldn't remove ${symbol} from your watchlist. Try again in a moment.`);
    }
  }
}
