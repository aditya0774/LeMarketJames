import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HoldingsSplit } from './holdings-split';

describe('HoldingsSplit', () => {
  const render = (inputs: Record<string, unknown>) => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HoldingsSplit);
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
    return fixture;
  };

  it('shows a loading line while holdings load', () => {
    expect(render({ loading: true }).nativeElement.querySelector('[role=status]').textContent).toContain('Chalking up');
  });

  it('shows the error instead of an empty donut', () => {
    const el: HTMLElement = render({ error: 'Unable to load holdings.' }).nativeElement;

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Unable to load holdings.');
    expect(el.querySelector('svg')).toBeNull();
  });

  it('asks to load a stock into quick trade', () => {
    const fixture = render({
      holdings: [{ symbol: 'NVDA', quantity: 1, averageCost: 1, currentPrice: 2, totalCost: 1, currentValue: 2, gainLoss: 1, gainLossPercent: 100 }],
    });
    const picked: string[] = [];
    fixture.componentInstance.selectSymbol.subscribe((s) => picked.push(s));

    (fixture.nativeElement.querySelector('li button') as HTMLButtonElement).click();

    expect(picked).toEqual(['NVDA']);
    expect(fixture.nativeElement.querySelector('.donut-val').textContent).toBe('$2');
  });
});
