import { TestBed } from '@angular/core/testing';
import { flagFor } from './insight-flag.fixtures';
import { InsightFlagChip } from './insight-flag-chip';
import { changeText, ChangedInsight, InsightFlags } from './insight-flags';
import { WhatChanged } from './what-changed';

function insight(key: string, changePercent: number | null): ChangedInsight {
  return { key, label: `Label ${key}`, comparedWith: `vs ${key}`, flag: flagFor(changePercent) };
}

describe('InsightFlags', () => {
  it('lists flagged insights first, then the largest moves, whichever tile reported them', () => {
    const flags = new InsightFlags();

    flags.report('volume', [insight('small', 3), insight('drop', -40)]);
    flags.report('stocks', [insight('rise', 26), insight('new', null), insight('mid', -12)]);

    expect(flags.all().map((i) => i.key)).toEqual(['new', 'drop', 'rise', 'mid', 'small']);
    expect(flags.thresholdPercent()).toBe(25);
  });

  it('a tile reporting again replaces what it reported before', () => {
    const flags = new InsightFlags();
    flags.report('volume', [insight('a', 40)]);
    flags.report('stocks', [insight('b', 1)]);

    flags.report('volume', [insight('c', 2)]);

    expect(flags.all().map((i) => i.key)).toEqual(['c', 'b']);
  });

  it('has nothing loaded until a tile reports', () => {
    const flags = new InsightFlags();

    expect(flags.loaded()).toBe(false);
    expect(flags.thresholdPercent()).toBeNull();
    flags.report('stocks', []);
    expect(flags.loaded()).toBe(true);
  });
});

describe('changeText', () => {
  it.each([
    [flagFor(30), '+30%'],
    [flagFor(-20.5), '−20.5%'],
    [flagFor(0), '0%'],
    [flagFor(1234.567), '+1,234.57%'],
    [flagFor(null), 'new'],
    [flagFor(null, 'FLAT'), 'no change'],
  ])('%o reads %s', (flag, text) => {
    expect(changeText(flag)).toBe(text);
  });
});

describe('InsightFlagChip', () => {
  function chip(flag: ReturnType<typeof flagFor> | null) {
    const fixture = TestBed.createComponent(InsightFlagChip);
    fixture.componentRef.setInput('flag', flag);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.flag');
  }

  it('says "Flagged", the move and its direction, in words as well as colour', () => {
    const rise = chip(flagFor(30))!;

    expect(rise.textContent!.replace(/\s+/g, ' ').trim()).toBe('▲ Flagged +30%');
    expect(rise.classList).toContain('up');
    expect(rise.getAttribute('title')).toBe('Moved by 25% or more against the period before');
    expect(chip(flagFor(-30))!.classList).toContain('down');
    expect(chip(flagFor(null))!.textContent).toContain('Flagged new');
  });

  it('shows nothing for an insight that is not flagged, or before it loads', () => {
    expect(chip(flagFor(10))).toBeNull();
    expect(chip(null)).toBeNull();
  });
});

describe('WhatChanged', () => {
  function create() {
    TestBed.configureTestingModule({ providers: [InsightFlags] });
    const fixture = TestBed.createComponent(WhatChanged);
    fixture.detectChanges();
    return { fixture, flags: TestBed.inject(InsightFlags), el: fixture.nativeElement as HTMLElement };
  }

  it('waits for the tiles', () => {
    const { el } = create();

    expect(el.textContent).toContain('Loading');
    expect(el.querySelector('li')).toBeNull();
  });

  it('lists flagged insights first with their chip, then the rest with their move', () => {
    const { fixture, flags, el } = create();

    flags.report('volume', [insight('calm', 5), insight('jump', 60)]);
    fixture.detectChanges();

    const rows = Array.from(el.querySelectorAll('li'));
    expect(rows.map((r) => r.getAttribute('data-key'))).toEqual(['jump', 'calm']);
    expect(rows[0].classList).toContain('flagged');
    expect(rows[0].querySelector('staff-insight-flag .flag')!.textContent).toContain('+60%');
    expect(rows[0].textContent).toContain('Label jump');
    expect(rows[0].textContent).toContain('vs jump');
    expect(rows[1].querySelector('staff-insight-flag')).toBeNull();
    expect(rows[1].querySelector('.change')!.textContent).toBe('+5%');
    expect(el.textContent).toContain('Flagged when an insight moves by 25% or more against the period before.');
    expect(el.textContent).not.toContain('Nothing moved');
  });

  it('says so when nothing moved enough', () => {
    const { fixture, flags, el } = create();

    flags.report('volume', [insight('calm', 5)]);
    fixture.detectChanges();

    expect(el.textContent).toContain('Nothing moved by 25% or more.');
    expect(el.querySelectorAll('li')).toHaveLength(1);
  });
});
