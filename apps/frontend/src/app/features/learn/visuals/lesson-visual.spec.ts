import { TestBed } from '@angular/core/testing';
import { LessonVisual } from './lesson-visual';
import { LESSONS } from '../learn-content';

describe('LessonVisual', () => {
  function render(id: string): HTMLElement {
    const fixture = TestBed.createComponent(LessonVisual);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const ids = LESSONS.flatMap((l) => (l.visual ? [l.visual] : []));

  it('is used by at least one lesson', () => {
    expect(ids.length).toBeGreaterThan(0);
  });

  for (const id of ids) {
    it(`draws "${id}" with a label and a caption that say the same thing`, () => {
      const el = render(id);

      const label = el.querySelector('svg')?.getAttribute('aria-label');
      expect(label).toBeTruthy();
      expect(el.querySelector('figcaption')?.textContent?.trim()).toBe(label);
      expect(el.querySelectorAll('svg text').length).toBeGreaterThan(0);
    });
  }

  it('draws nothing for an id it has no diagram for', () => {
    expect(render('nope').querySelector('figure')).toBeNull();
  });
});
