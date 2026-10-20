import { TestBed } from '@angular/core/testing';
import { LessonText } from './lesson-text';
import { TextSegment } from '../term-links';

const SHARE = { term: 'Share', definition: 'One unit of ownership.' };
const FILL = { term: 'Fill', definition: 'When an order executes.' };

const SEGMENTS: TextSegment[] = [
  { text: 'A ' },
  { text: 'share', term: SHARE },
  { text: ' is bought when an order ' },
  { text: 'fills', term: FILL },
  { text: '.' },
];

describe('LessonText', () => {
  function render(segments: TextSegment[] = SEGMENTS) {
    const fixture = TestBed.createComponent(LessonText);
    fixture.componentRef.setInput('segments', segments);
    fixture.detectChanges();
    return fixture;
  }

  const buttons = (f: ReturnType<typeof render>) => Array.from<HTMLButtonElement>(f.nativeElement.querySelectorAll('.term-link'));
  const definition = (f: ReturnType<typeof render>): HTMLElement | null => f.nativeElement.querySelector('.term-def');

  function click(fixture: ReturnType<typeof render>, button: HTMLButtonElement): void {
    button.click();
    fixture.detectChanges();
  }

  it('prints the paragraph unchanged, with each term as a button', () => {
    const fixture = render();

    expect(fixture.nativeElement.querySelector('p').textContent.replace(/\s+/g, ' ').trim()).toBe(
      'A share is bought when an order fills.',
    );
    expect(buttons(fixture).map((b) => b.textContent)).toEqual(['share', 'fills']);
  });

  it('shows no definition until a term is opened', () => {
    const fixture = render();

    expect(definition(fixture)).toBeNull();
    expect(buttons(fixture).every((b) => b.getAttribute('aria-expanded') === 'false')).toBe(true);
  });

  it('shows the definition of the term that was tapped', () => {
    const fixture = render();

    click(fixture, buttons(fixture)[0]);

    expect(definition(fixture)?.textContent).toContain('Share');
    expect(definition(fixture)?.textContent).toContain('One unit of ownership.');
    expect(buttons(fixture)[0].getAttribute('aria-expanded')).toBe('true');
  });

  it('closes the definition when the same term is tapped again', () => {
    const fixture = render();

    click(fixture, buttons(fixture)[0]);
    click(fixture, buttons(fixture)[0]);

    expect(definition(fixture)).toBeNull();
  });

  it('switches to another term instead of showing two', () => {
    const fixture = render();

    click(fixture, buttons(fixture)[0]);
    click(fixture, buttons(fixture)[1]);

    expect(fixture.nativeElement.querySelectorAll('.term-def').length).toBe(1);
    expect(definition(fixture)?.textContent).toContain('When an order executes.');
    expect(buttons(fixture)[0].getAttribute('aria-expanded')).toBe('false');
  });

  it('closes on Escape', () => {
    const fixture = render();
    click(fixture, buttons(fixture)[0]);

    fixture.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(definition(fixture)).toBeNull();
  });

  it('renders text without terms as plain text', () => {
    const fixture = render([{ text: 'Nothing special here.' }]);

    expect(buttons(fixture).length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Nothing special here.');
  });
});
