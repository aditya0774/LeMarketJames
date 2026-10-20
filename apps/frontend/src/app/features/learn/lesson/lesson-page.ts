import { Component, ElementRef, afterNextRender, computed, effect, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { KeyTerms } from '../key-terms/key-terms';
import { LearnProgress } from '../learn-progress';
import { entryFor } from '../lesson-entries';
import { LessonText } from '../lesson-text/lesson-text';
import { LESSONS } from '../learn-content';
import { LessonVisual } from '../visuals/lesson-visual';

/**
 * One Learn lesson on its own page (`/learn/:lessonId`), with Back and Next through all lessons in
 * order. Next (and Finish on the last lesson) counts as having read the lesson, so progress fills in
 * as a reader works through; "Mark as read" stays for doing it by hand.
 *
 * The page composes the same text, diagram and key terms components the content was written for;
 * what to show comes from lesson-entries.ts and "read" state from LearnProgress.
 */
@Component({
  selector: 'app-lesson-page',
  imports: [RouterLink, KeyTerms, LessonText, LessonVisual],
  templateUrl: './lesson-page.html',
  styleUrl: './lesson-page.css',
})
export class LessonPage {
  private readonly router = inject(Router);
  protected readonly progress = inject(LearnProgress);

  private readonly lessonId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('lessonId'))),
    { initialValue: null },
  );

  /** The lesson for the URL, or undefined for an id that is not a lesson. */
  protected readonly entry = computed(() => entryFor(this.lessonId()));
  protected readonly total = LESSONS.length;
  protected readonly percent = computed(() => ((this.entry()?.number ?? 0) / this.total) * 100);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  constructor() {
    // The same component serves every lesson, so moving between them must bring the reader back to
    // the top, with focus on the new title, as a page change would.
    effect(() => {
      this.lessonId();
      const heading = this.heading();
      if (heading) {
        window.scrollTo({ top: 0 });
        heading.nativeElement.focus({ preventScroll: true });
      }
    });
    afterNextRender(() => this.heading()?.nativeElement.focus({ preventScroll: true }));
  }

  /** Reading on counts as having read this lesson; the last lesson's Finish returns to the index. */
  protected next(): void {
    const entry = this.entry();
    if (!entry) {
      return;
    }
    this.progress.markRead(entry.lesson.id);
    void this.router.navigate(entry.next ? ['/learn', entry.next.id] : ['/learn']);
  }

  protected back(): void {
    const prev = this.entry()?.prev;
    if (prev) {
      void this.router.navigate(['/learn', prev.id]);
    }
  }

  protected toggleRead(): void {
    const entry = this.entry();
    if (entry) {
      this.progress.toggle(entry.lesson.id);
    }
  }
}
