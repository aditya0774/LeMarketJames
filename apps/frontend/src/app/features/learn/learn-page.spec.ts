import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
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

  type Fixture = ReturnType<typeof render>;
  const tiles = (f: Fixture) => Array.from<HTMLAnchorElement>(f.nativeElement.querySelectorAll('.lesson'));
  const text = (el: HTMLElement) => el.textContent!.replace(/\s+/g, ' ').trim();

  it('shows a tile for every lesson, numbered in order', () => {
    const titles = tiles(render()).map((t) => t.querySelector('h2')?.textContent?.trim());

    expect(titles).toEqual(LESSONS.map((l, i) => `${i + 1}. ${l.title}`));
  });

  it('opens a lesson in the reader when its tile is clicked', () => {
    const hrefs = tiles(render()).map((t) => t.getAttribute('href'));

    expect(hrefs).toEqual(LESSONS.map((l) => `/learn/${l.id}`));
  });

  it('shows no lesson text on the index, only the summary', () => {
    const fixture = render();

    expect(fixture.nativeElement.querySelector('details')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-lesson-text')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-key-terms')).toBeNull();
    expect(text(tiles(fixture)[0])).toContain(LESSONS[0].summary);
    expect(text(tiles(fixture)[0])).not.toContain(LESSONS[0].body[0]);
  });

  it('links to the Market tab and the stock simulator, not to a real trade', () => {
    const fixture = render();

    const hrefs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('.learn-actions a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toEqual(['/market', '/learn/simulator']);
    expect(fixture.nativeElement.textContent).not.toContain('practice trade');
  });

  it('marks a lesson as read on its own tile, and only that one', () => {
    TestBed.inject(LearnProgress).markRead(LESSONS[1].id);
    const fixture = render();

    const marked = tiles(fixture)
      .filter((t) => t.querySelector('.lesson-read-mark'))
      .map((t) => t.getAttribute('href'));
    expect(marked).toEqual([`/learn/${LESSONS[1].id}`]);
  });

  describe('what to read next', () => {
    const banner = (f: Fixture): HTMLElement => f.nativeElement.querySelector('.learn-next');

    it('says where to start when nothing is read, and links to the first lesson', () => {
      const el = banner(render());

      expect(text(el)).toContain('Start here');
      expect(text(el)).toContain(LESSONS[0].title);
      expect(text(el)).toContain('Start the first lesson');
      expect(el.querySelector('a')?.getAttribute('href')).toBe(`/learn/${LESSONS[0].id}`);
    });

    it('points at the first unread lesson once some are read', () => {
      const progress = TestBed.inject(LearnProgress);
      progress.markRead(LESSONS[0].id);
      progress.markRead(LESSONS[1].id);

      const el = banner(render());

      expect(text(el)).toContain('Up next');
      expect(text(el)).toContain(LESSONS[2].title);
      expect(text(el)).toContain('Continue');
      expect(el.querySelector('a')?.getAttribute('href')).toBe(`/learn/${LESSONS[2].id}`);
    });

    it('counts the lessons read', () => {
      TestBed.inject(LearnProgress).markRead(LESSONS[0].id);

      expect(text(banner(render()))).toContain(`1 of ${LESSONS.length} lessons read`);
    });

    it('says so when every lesson is read, and points at the simulator', () => {
      const progress = TestBed.inject(LearnProgress);
      LESSONS.forEach((l) => progress.markRead(l.id));
      const el = banner(render());

      expect(text(el)).toContain('read every lesson');
      expect(el.querySelector('a')?.getAttribute('href')).toBe('/learn/simulator');
      expect(text(el)).not.toContain('Trade tab');
    });
  });

  describe('level filter', () => {
    function pick(fixture: Fixture, label: string): void {
      const chip = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.learn-filter .chip')).find(
        (c) => c.textContent?.trim() === label,
      );
      chip!.click();
      fixture.detectChanges();
    }

    it('shows every lesson by default', () => {
      expect(tiles(render()).length).toBe(LESSONS.length);
    });

    it('shows only the chosen level but keeps the suggested numbering', () => {
      const fixture = render();

      pick(fixture, 'Intermediate');

      const expected = LESSONS.map((l, i) => ({ l, n: i + 1 })).filter((e) => e.l.level === 'intermediate');
      const titles = tiles(fixture).map((t) => t.querySelector('h2')?.textContent?.trim());
      expect(titles).toEqual(expected.map((e) => `${e.n}. ${e.l.title}`));
    });

    it('does not change what is suggested next', () => {
      const fixture = render();

      pick(fixture, 'Intermediate');

      expect(text(fixture.nativeElement.querySelector('.learn-next'))).toContain(LESSONS[0].title);
    });
  });
});
