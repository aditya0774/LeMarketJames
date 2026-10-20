import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { LearnPage } from './learn-page';
import { LESSONS } from './learn-content';
import { LearnProgress } from './learn-progress';

describe('LearnPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem('learn.read');
    TestBed.configureTestingModule({ imports: [LearnPage], providers: [provideRouter([])] });
  });

  function render() {
    const fixture = TestBed.createComponent(LearnPage);
    fixture.detectChanges();
    return fixture;
  }

  it('shows every lesson', () => {
    const fixture = render();

    const titles = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.lesson h2')).map((h) =>
      h.textContent?.trim(),
    );
    expect(titles).toEqual(LESSONS.map((l, i) => `${i + 1}. ${l.title}`));
  });

  it('links to the Market and Trade tabs', () => {
    const fixture = render();

    const hrefs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('.learn-actions a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toEqual(['/market', '/trade']);
  });

  it('gives every lesson an anchor for the banner and next-lesson links, and no second list of them', () => {
    const fixture = render();

    const ids = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.lesson')).map((l) => l.id);
    expect(ids).toEqual(LESSONS.map((l) => l.id));
    expect(fixture.nativeElement.querySelector('.learn-toc')).toBeNull();
  });

  it('marks a lesson as read on its own card, and only that one', () => {
    const progress = TestBed.inject(LearnProgress);
    progress.toggle(LESSONS[1].id);
    const fixture = render();

    const marked = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.lesson'))
      .filter((card) => card.querySelector('.lesson-read-mark'))
      .map((card) => card.id);
    expect(marked).toEqual([LESSONS[1].id]);
  });

  it('links each lesson that has one to the tab where it can be practised', () => {
    const fixture = render();

    const expected = LESSONS.filter((l) => l.cta).map((l) => l.cta!.route);
    const hrefs = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll('.lesson-actions a'),
    ).map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(expected);
  });

  it('starts with every lesson collapsed and nothing read', () => {
    const fixture = render();

    expect(fixture.nativeElement.querySelectorAll('.lesson[open]').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.learn-progress').textContent).toContain(
      `0 of ${LESSONS.length} lessons read`,
    );
  });

  it('counts a lesson as read when marked, and remembers it', () => {
    const fixture = render();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.lesson-read');
    button.click();
    fixture.detectChanges();

    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.querySelector('.learn-progress').textContent).toContain(
      `1 of ${LESSONS.length} lessons read`,
    );
    expect(JSON.parse(localStorage.getItem('learn.read')!)).toEqual([LESSONS[0].id]);

    button.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('still works when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const fixture = render();

    fixture.nativeElement.querySelector('.lesson-read').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.learn-progress').textContent).toContain('1 of');
  });

  describe('what to read next', () => {
    const banner = (fixture: ReturnType<typeof render>): HTMLElement => fixture.nativeElement.querySelector('.learn-next');

    it('says where to start when nothing is read', () => {
      const text = banner(render()).textContent;

      expect(text).toContain('Start here');
      expect(text).toContain(LESSONS[0].title);
    });

    it('points at the first unread lesson once some are read', () => {
      const progress = TestBed.inject(LearnProgress);
      progress.toggle(LESSONS[0].id);
      progress.toggle(LESSONS[1].id);
      const fixture = render();

      const el = banner(fixture);
      expect(el.textContent).toContain('Up next');
      expect(el.textContent).toContain(LESSONS[2].title);
      expect(el.querySelector('a')?.getAttribute('href')).toContain(`#${LESSONS[2].id}`);
    });

    it('says so when every lesson is read', () => {
      const progress = TestBed.inject(LearnProgress);
      LESSONS.forEach((l) => progress.toggle(l.id));
      const el = banner(render());

      expect(el.textContent).toContain('read every lesson');
      expect(el.querySelector('a')).toBeNull();
    });

    it('offers the next lesson after one is marked read, but not before', () => {
      const fixture = render();
      expect(fixture.nativeElement.querySelector('.lesson-next')).toBeNull();

      fixture.nativeElement.querySelector('.lesson-read').click();
      fixture.detectChanges();

      const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.lesson-next');
      expect(link.textContent).toContain(LESSONS[1].title);
      expect(link.getAttribute('href')).toContain(`#${LESSONS[1].id}`);
    });

    it('offers no next lesson after the last one', () => {
      const progress = TestBed.inject(LearnProgress);
      progress.toggle(LESSONS[LESSONS.length - 1].id);
      const fixture = render();

      const cards = fixture.nativeElement.querySelectorAll('.lesson');
      expect(cards[cards.length - 1].querySelector('.lesson-next')).toBeNull();
    });
  });

  describe('level filter', () => {
    function pick(fixture: ReturnType<typeof render>, label: string): void {
      const chip = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.learn-filter .chip')).find(
        (c) => c.textContent?.trim() === label,
      );
      chip!.click();
      fixture.detectChanges();
    }

    it('shows every lesson by default', () => {
      const fixture = render();

      expect(fixture.nativeElement.querySelectorAll('.lesson').length).toBe(LESSONS.length);
    });

    it('shows only the chosen level but keeps the suggested numbering', () => {
      const fixture = render();

      pick(fixture, 'Intermediate');

      const expected = LESSONS.map((l, i) => ({ l, n: i + 1 })).filter((e) => e.l.level === 'intermediate');
      const titles = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.lesson h2')).map((h) =>
        h.textContent?.trim(),
      );
      expect(titles).toEqual(expected.map((e) => `${e.n}. ${e.l.title}`));
      expect(fixture.nativeElement.querySelectorAll('.lesson').length).toBe(expected.length);
    });

    it('does not change what is suggested next', () => {
      const fixture = render();

      pick(fixture, 'Intermediate');

      expect(fixture.nativeElement.querySelector('.learn-next').textContent).toContain(LESSONS[0].title);
    });
  });

  describe('glossary terms', () => {
    it('no longer has a standalone glossary', () => {
      const fixture = render();

      expect(fixture.nativeElement.querySelector('app-glossary')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Search terms');
    });

    it('shows each lesson\'s key terms as chips, in the order the lesson lists them', () => {
      const fixture = render();

      const cards: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.lesson'));
      cards.forEach((card, i) => {
        const names = Array.from<HTMLElement>(card.querySelectorAll('.key-terms .chip')).map((c) => c.textContent?.trim());
        expect(names).toEqual(LESSONS[i].terms);
      });
    });

    it('opens a key term\'s definition in the box when its chip is clicked', () => {
      const fixture = render();
      const card: HTMLElement = fixture.nativeElement.querySelector('#orders');

      const chip: HTMLButtonElement = card.querySelector('.key-terms .chip')!;
      chip.click();
      fixture.detectChanges();

      const definition = card.querySelector<HTMLElement>('.key-terms-def')!;
      expect(definition.hidden).toBe(false);
      expect(definition.textContent).toContain(chip.textContent!.trim());
    });

    it('marks a term in the lesson text and opens its definition when tapped', () => {
      const fixture = render();
      const card: HTMLElement = fixture.nativeElement.querySelector('#basics');

      const term: HTMLButtonElement = card.querySelector('.term-link')!;
      term.click();
      fixture.detectChanges();

      // "A share is..." opens the Share definition, whatever the case or plural in the text.
      expect(card.querySelector('.term-def')?.textContent?.toLowerCase()).toContain('share');
      expect(term.getAttribute('aria-expanded')).toBe('true');
    });
  });

  it('draws the diagram of each lesson that has one', () => {
    const fixture = render();

    expect(fixture.nativeElement.querySelectorAll('.lesson figure').length).toBe(
      LESSONS.filter((l) => l.visual).length,
    );
  });
});
