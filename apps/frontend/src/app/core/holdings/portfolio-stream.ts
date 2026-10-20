import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Live portfolio stream (GET /api/v1/portfolio/stream, contract C6).
 *
 * Emits whenever the signed-in client's holdings and cash should be re-read: on each
 * `balance-changed` event, and when the stream reopens after dropping. The server keeps no
 * replay, so a reconnect is the only moment a missed settlement can be caught up on.
 */
@Injectable({ providedIn: 'root' })
export class PortfolioStream {
  private readonly url = `${environment.apiBaseUrl}/api/v1/portfolio/stream`;

  refreshes(): Observable<void> {
    return new Observable<void>((subscriber) => {
      const stream = new EventSource(this.url);
      let dropped = false;

      stream.onerror = () => (dropped = true);
      stream.onopen = () => {
        // The first open needs no refresh: the page has just loaded the account itself.
        if (dropped) {
          dropped = false;
          subscriber.next();
        }
      };
      stream.addEventListener('balance-changed', () => subscriber.next());

      return () => stream.close();
    });
  }
}
