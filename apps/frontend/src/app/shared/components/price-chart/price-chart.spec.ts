import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChartPoint } from '../../models/chart.model';
import { PriceChart } from './price-chart';

const DAY = 86_400_000;
const START = Date.parse('2026-09-15T20:00:00Z');
const POINTS: ChartPoint[] = [
  { time: START, value: 1000 },
  { time: START + DAY, value: 1100 },
  { time: START + 2 * DAY, value: 1050 },
];

describe('PriceChart', () => {
  let fixture: ComponentFixture<PriceChart>;
  const el = () => fixture.nativeElement as HTMLElement;

  function render(inputs: Record<string, unknown>) {
    fixture = TestBed.createComponent(PriceChart);
    for (const [name, value] of Object.entries({ heading: 'Portfolio value', ...inputs })) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
  }

  it('shows the latest value and the change over the range', () => {
    render({ points: POINTS, range: '1M' });

    expect(el().querySelector('h2')?.textContent).toBe('Portfolio value');
    expect(el().querySelector('.pc-value')?.textContent).toBe('$1,050.00');
    expect(el().querySelector('.pc-change')?.textContent).toBe('▲ +$50.00 (+5.00%) past month');
    expect(el().querySelector('.pc-change')?.classList).toContain('up');
    expect(el().querySelector('.pc-plot')?.getAttribute('aria-label')).toBe(
      'Portfolio value past month, from $1,000.00 to $1,050.00',
    );
  });

  it('offers the range switch unless a page hides it', () => {
    render({ points: POINTS });
    expect(el().querySelectorAll('.pc-ranges button').length).toBe(5);

    render({ points: POINTS, showRanges: false });
    expect(el().querySelector('.pc-ranges')).toBeNull();
  });

  it('can describe the span in its own words instead of the range\'s', () => {
    render({ points: POINTS, range: '3M', spanLabel: 'since you bought' });

    expect(el().querySelector('.pc-change')?.textContent).toBe('▲ +$50.00 (+5.00%) since you bought');
    expect(el().querySelector('.pc-plot')?.getAttribute('aria-label')).toContain('since you bought');
  });

  it('can leave the value out', () => {
    render({ points: POINTS, showValue: false });

    expect(el().querySelector('.pc-value')).toBeNull();
    expect(el().querySelector('.pc-change')).not.toBeNull();
  });

  it('marks the chosen range and reports a new one', () => {
    render({ points: POINTS, range: '3M' });
    const chosen: string[] = [];
    fixture.componentInstance.rangeChange.subscribe((r) => chosen.push(r));

    const buttons = Array.from(el().querySelectorAll<HTMLButtonElement>('.pc-ranges button'));
    expect(buttons.map((b) => b.textContent)).toEqual(['1D', '1W', '1M', '3M', '6M']);
    expect(buttons.find((b) => b.getAttribute('aria-pressed') === 'true')?.textContent).toBe('3M');

    buttons[4].click();
    expect(chosen).toEqual(['6M']);
  });

  it('steps through the points with the arrow keys', () => {
    render({ points: POINTS, range: '1M' });
    const track = el().querySelector('.pc-track') as HTMLElement;

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();

    const tip = el().querySelector('.pc-tip');
    expect(tip?.querySelector('.pc-tip-val')?.textContent).toBe('$1,100.00');
    expect(tip?.querySelector('.pc-tip-time')?.textContent).toBe('Sep 16, 2026');
    expect(tip?.querySelector('.pc-tip-chg')?.textContent).toBe('▲ +$100.00 (+10.00%) since Sep 15, 2026');

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip')).toBeNull();
  });

  it('follows the pointer across the plot and hides the tooltip when it leaves', () => {
    render({ points: POINTS, range: '1M' });
    const track = el().querySelector('.pc-track') as HTMLElement;
    track.getBoundingClientRect = () => ({ left: 100, width: 200 }) as DOMRect;

    track.dispatchEvent(new MouseEvent('mousemove', { clientX: 300 }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip-val')?.textContent).toBe('$1,050.00');

    // Past the left edge counts as the first point.
    track.dispatchEvent(new MouseEvent('mousemove', { clientX: 20 }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip-val')?.textContent).toBe('$1,000.00');

    track.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip')).toBeNull();
  });

  it('starts the arrow keys from the latest point, steps back, and clears when focus leaves', () => {
    render({ points: POINTS, range: '1M' });
    const track = el().querySelector('.pc-track') as HTMLElement;

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip-val')?.textContent).toBe('$1,050.00');

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip-val')?.textContent).toBe('$1,100.00');

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip-val')?.textContent).toBe('$1,050.00');

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));
    track.dispatchEvent(new FocusEvent('blur'));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip')).toBeNull();
  });

  it('marks a fall over the range and puts the tooltip low over a high point', () => {
    render({ points: [{ time: START, value: 1200 }, { time: START + DAY, value: 900 }], range: '1W' });
    expect(el().querySelector('.pc-change')?.classList).toContain('down');

    const track = el().querySelector('.pc-track') as HTMLElement;
    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    fixture.detectChanges();
    expect(el().querySelector('.pc-tip')?.classList).toContain('low');
    expect(el().querySelector('.pc-tip-chg')?.classList).toContain('up');
  });

  it('draws a single point in the middle of the chart', () => {
    render({ points: [{ time: START, value: 500 }], range: '1D' });
    const track = el().querySelector('.pc-track') as HTMLElement;

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    fixture.detectChanges();

    expect((el().querySelector('.pc-dot') as HTMLElement).style.left).toBe('50%');
  });

  it('describes an empty chart for screen readers', () => {
    render({ points: [] });
    expect((fixture.componentInstance as any).summary()).toBe('Portfolio value: no data');
  });

  it('shows the loading, error and empty states', () => {
    render({ loading: true });
    expect(el().querySelector('output.loading')).not.toBeNull();

    render({ error: 'Prices are temporarily unavailable.' });
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Prices are temporarily unavailable.');

    render({ points: [], emptyText: 'Buy a stock first.' });
    expect(el().querySelector('.empty-state')?.textContent).toContain('Buy a stock first.');
    expect(el().querySelector('.pc-value')).toBeNull();
  });
});
