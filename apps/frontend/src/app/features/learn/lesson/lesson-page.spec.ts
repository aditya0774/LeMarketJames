import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';
import { LESSONS } from '../learn-content';
import { LearnProgress } from '../learn-progress';
import { LessonPage } from './lesson-page';

/** Stands in for the Learn index, which Finish returns to. */
@Component({ selector: 'app-index-stub', template: 'index' })
class IndexStub {}

describe('LessonPage', () => {
  let harness: RouterTestingHarness;
  const el = () => harness.fixture.nativeElement as HTMLElement;
  const text = () => el().textContent!.replace(/\s+/g, ' ').trim();
  const progress = () => TestBed.inject(LearnProgress);

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem('learn.read');
    window.scrollTo = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'learn', component: IndexStub },
          { path: 'learn/:lessonId', component: LessonPage },
        ]),
      ],
    });
  });

  async function open(id: string) {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/learn/${id}`, LessonPage);
    harness.detectChanges();
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim().startsWith(label))!;
  }

  async function click(label: string) {
    button(label).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  const url = () => TestBed.inject(Router).url;
  const first = LESSONS[0];
  const last = LESSONS[LESSONS.length - 1];

  it('shows the lesson for the URL: title, summary, text and where it sits', async () => {
    await open('orders');
    const lesson = LESSONS.find((l) => l.id === 'orders')!;
    const number = LESSONS.indexOf(lesson) + 1;

    expect(el().querySelector('h1')?.textContent).toBe(lesson.title);
    expect(text()).toContain(lesson.summary);
    expect(text()).toContain(`Lesson ${number} of ${LESSONS.length}`);
    expect(el().querySelectorAll('app-lesson-text').length).toBe(lesson.body.length);
    expect(el().querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe(String(number));
  });

  it('shows the diagram, key terms and practise link of a lesson that has them', async () => {
    await open('orders');
    const lesson = LESSONS.find((l) => l.id === 'orders')!;

    expect(el().querySelector('app-lesson-visual figure')).not.toBeNull();
    expect(Array.from(el().querySelectorAll('.key-terms .chip')).map((c) => c.textContent!.trim())).toEqual(lesson.terms);
    expect(el().querySelector('.reader-actions a')?.getAttribute('href')).toBe(lesson.cta!.route);
  });

  it('has no Back on the first lesson, and says Next', async () => {
    await open(first.id);

    expect(button('← Back')).toBeUndefined();
    expect(button('Next')).toBeDefined();
  });

  it('goes to the next lesson with Next and counts the one just read as read', async () => {
    await open(first.id);
    expect(progress().isRead(first.id)).toBe(false);

    await click('Next');

    expect(url()).toBe(`/learn/${LESSONS[1].id}`);
    expect(el().querySelector('h1')?.textContent).toBe(LESSONS[1].title);
    expect(progress().isRead(first.id)).toBe(true);
    expect(progress().isRead(LESSONS[1].id)).toBe(false);
  });

  it('goes back with Back, without changing what has been read', async () => {
    await open(LESSONS[1].id);

    await click('← Back');

    expect(url()).toBe(`/learn/${first.id}`);
    expect(el().querySelector('h1')?.textContent).toBe(first.title);
    expect(progress().isRead(LESSONS[1].id)).toBe(false);
  });

  it('walks all the way through in order', async () => {
    await open(first.id);

    for (const lesson of LESSONS.slice(1)) {
      await click('Next');
      expect(el().querySelector('h1')?.textContent).toBe(lesson.title);
    }
    expect(LESSONS.slice(0, -1).every((l) => progress().isRead(l.id))).toBe(true);
  });

  it('says Finish on the last lesson, counts it read and returns to the index', async () => {
    await open(last.id);
    expect(button('Next')).toBeUndefined();

    await click('Finish');

    expect(url()).toBe('/learn');
    expect(progress().isRead(last.id)).toBe(true);
  });

  it('keeps a lesson read when Next is used on it again', async () => {
    progress().markRead(first.id);
    await open(first.id);

    await click('Next');

    expect(progress().isRead(first.id)).toBe(true);
  });

  it('marks a lesson read by hand, and undoes it', async () => {
    await open(first.id);

    await click('Mark as read');
    expect(progress().isRead(first.id)).toBe(true);
    expect(text()).toContain('✓ Read');
    expect(button('Read').getAttribute('aria-pressed')).toBe('true');

    await click('Read');
    expect(progress().isRead(first.id)).toBe(false);
  });

  it('brings the reader to the top, with focus on the new title, when the lesson changes', async () => {
    await open(first.id);
    (window.scrollTo as ReturnType<typeof vi.fn>).mockClear();

    await click('Next');

    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0 });
    expect(document.activeElement).toBe(el().querySelector('h1'));
  });

  it('says so, and links back, for a lesson that does not exist', async () => {
    await open('nope');

    expect(text()).toContain("We couldn't find that lesson");
    expect(el().querySelector('a')?.getAttribute('href')).toBe('/learn');
    expect(el().querySelector('[role="progressbar"]')).toBeNull();
  });

  it('links back to all lessons', async () => {
    await open(first.id);

    expect(el().querySelector('.reader-back')?.getAttribute('href')).toBe('/learn');
  });
});
