import { TestBed } from '@angular/core/testing';
import { SimulationSummary } from './simulation-summary';
import { Simulation } from './simulator-math';

const GAIN: Simulation = {
  buyDate: '2026-05-08',
  buyPrice: 50,
  shares: 20.12345,
  currentPrice: 75,
  currentValue: 1234.5,
  gain: 234.5,
  gainPercent: 23.45,
  daysHeld: 30,
  series: [],
};

describe('SimulationSummary', () => {
  function render(simulation: Simulation) {
    const fixture = TestBed.createComponent(SimulationSummary);
    fixture.componentRef.setInput('simulation', simulation);
    fixture.componentRef.setInput('stockName', 'BronApple');
    fixture.componentRef.setInput('amount', 1000);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ').trim();

  it('tells what was put in, what it bought and what it is worth now', () => {
    const result = text(render(GAIN));

    expect(result).toContain('Invested$1,000.00');
    expect(result).toContain('20.1235 shares of BronApple at $50.00 on May 8, 2026');
    expect(result).toContain('Worth today$1,234.50');
  });

  it('shows a gain with the word, an up arrow, a plus sign and the up class', () => {
    const el = render(GAIN);

    expect(text(el)).toContain('Theoretical gain+$234.50▲ +23.45%');
    expect(el.querySelectorAll('.up').length).toBe(2);
    expect(el.querySelector('.down')).toBeNull();
  });

  it('shows a loss with the word, a down arrow, a minus sign and the down class', () => {
    const el = render({ ...GAIN, gain: -250, gainPercent: -25, currentValue: 750 });

    expect(text(el)).toContain('Theoretical loss−$250.00▼ −25.00%');
    expect(el.querySelectorAll('.down').length).toBe(2);
    expect(el.querySelector('.up')).toBeNull();
  });

  it('counts no change as a gain of nothing', () => {
    expect(text(render({ ...GAIN, gain: 0, gainPercent: 0, daysHeld: 0 }))).toContain('Theoretical gain+$0.00▲ +0.00%');
  });

  it('explains a purchase made today', () => {
    expect(text(render({ ...GAIN, daysHeld: 0 }))).toContain('No time has passed');
    expect(text(render(GAIN))).not.toContain('No time has passed');
  });
});
