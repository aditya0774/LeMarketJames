import { TestBed } from '@angular/core/testing';
import { KeyTerms } from './key-terms';
import { GlossaryTerm } from '../learn-content';

const TERMS: GlossaryTerm[] = [
  { term: 'Bid', definition: 'The highest price a buyer will pay.' },
  { term: 'Ask', definition: 'The lowest price a seller will accept.' },
];

describe('KeyTerms', () => {
  function render(terms: GlossaryTerm[] = TERMS) {
    const fixture = TestBed.createComponent(KeyTerms);
    fixture.componentRef.setInput('terms', terms);
    fixture.detectChanges();
    return fixture;
  }

  type Fixture = ReturnType<typeof render>;
  const chips = (f: Fixture) => Array.from<HTMLButtonElement>(f.nativeElement.querySelectorAll('.chip'));
  const panel = (f: Fixture): HTMLElement => f.nativeElement.querySelector('.key-terms-def');

  function click(fixture: Fixture, chip: HTMLButtonElement): void {
    chip.click();
    fixture.detectChanges();
  }

  it('shows one chip per term, in the order given', () => {
    expect(chips(render()).map((c) => c.textContent?.trim())).toEqual(['Bid', 'Ask']);
  });

  it('shows no definition until a chip is clicked', () => {
    const fixture = render();

    expect(panel(fixture).hidden).toBe(true);
    expect(chips(fixture).every((c) => c.getAttribute('aria-expanded') === 'false')).toBe(true);
  });

  it('shows the definition of the clicked term and marks its chip', () => {
    const fixture = render();

    click(fixture, chips(fixture)[0]);

    expect(panel(fixture).hidden).toBe(false);
    expect(panel(fixture).textContent).toContain('Bid');
    expect(panel(fixture).textContent).toContain('The highest price a buyer will pay.');
    expect(chips(fixture)[0].getAttribute('aria-expanded')).toBe('true');
    expect(chips(fixture)[0].classList).toContain('active');
  });

  it('points every chip at the definition panel', () => {
    const fixture = render();

    const ids = new Set(chips(fixture).map((c) => c.getAttribute('aria-controls')));

    expect([...ids]).toEqual([panel(fixture).id]);
  });

  it('closes the definition when the same chip is clicked again', () => {
    const fixture = render();

    click(fixture, chips(fixture)[0]);
    click(fixture, chips(fixture)[0]);

    expect(panel(fixture).hidden).toBe(true);
  });

  it('switches to another term instead of showing two', () => {
    const fixture = render();

    click(fixture, chips(fixture)[0]);
    click(fixture, chips(fixture)[1]);

    expect(panel(fixture).textContent).toContain('The lowest price a seller will accept.');
    expect(panel(fixture).textContent).not.toContain('highest');
    expect(chips(fixture)[0].getAttribute('aria-expanded')).toBe('false');
  });

  it('closes on Escape', () => {
    const fixture = render();
    click(fixture, chips(fixture)[0]);

    fixture.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(panel(fixture).hidden).toBe(true);
  });

  it('draws nothing when there are no terms', () => {
    const fixture = render([]);

    expect(fixture.nativeElement.querySelector('.key-terms')).toBeNull();
  });

  it('gives each box its own panel id', () => {
    const a = render();
    const b = render();

    expect(panel(a).id).not.toBe(panel(b).id);
  });
});
