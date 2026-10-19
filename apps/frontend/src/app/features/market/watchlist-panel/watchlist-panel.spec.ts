import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { WatchlistPanel } from './watchlist-panel';

describe('WatchlistPanel', () => {
  let fixture: ComponentFixture<WatchlistPanel>;
  const el = () => fixture.nativeElement as HTMLElement;

  function render(inputs: Record<string, unknown>) {
    TestBed.configureTestingModule({ imports: [WatchlistPanel], providers: [provideRouter([])] });
    fixture = TestBed.createComponent(WatchlistPanel);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
  }

  it('shows a spinner while the watchlist loads', () => {
    render({ status: 'loading' });

    expect(el().querySelector('output.loading')?.textContent).toContain('Chalking up');
    expect(el().querySelector('.count')).toBeNull();
  });

  it('offers to try again when the watchlist fails to load', () => {
    render({ status: 'error' });
    let retries = 0;
    fixture.componentInstance.retry.subscribe(() => retries++);

    expect(el().querySelector('[role="alert"]')?.textContent).toContain("Couldn't load your watchlist");
    (el().querySelector('.retry') as HTMLButtonElement).click();

    expect(retries).toBe(1);
  });

  it('shows a failed add or remove above the list', () => {
    render({ status: 'ready', changeError: "Couldn't add AAPL to your watchlist. Try again in a moment." });

    expect(el().querySelector('[role="alert"]')?.textContent).toContain("Couldn't add AAPL");
    expect(el().querySelector('.count')?.getAttribute('aria-label')).toBe('0 watched');
  });

  it('shows a dash for a watched stock whose price has not arrived yet', () => {
    render({
      status: 'ready',
      rows: [{ instrument: { instrumentId: 1, symbol: 'AAPL', name: 'BronApple', tradable: true }, quote: null, history: [] }],
    });

    expect(el().querySelector('.wl-price')?.textContent?.trim()).toBe('—');
    expect(el().querySelector('.chg')).toBeNull();
  });
});
