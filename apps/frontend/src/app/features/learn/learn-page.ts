import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { KeyTerms } from './key-terms/key-terms';
import { LessonText } from './lesson-text/lesson-text';
import { LessonVisual } from './visuals/lesson-visual';
import { LearnProgress } from './learn-progress';
import { GlossaryTerm, Lesson, LESSONS, LessonLevel } from './learn-content';
import { splitLesson, termsOf, TextSegment } from './term-links';

/** A lesson with its place in the suggested reading order, which a filter never changes. */
interface LessonEntry {
  lesson: Lesson;
  number: number;
  /** The body paragraphs with the lesson's glossary terms marked, ready to show. */
  paragraphs: TextSegment[][];
  /** The terms the lesson teaches, shown as clickable chips in its key terms box. */
  terms: GlossaryTerm[];
  /** The lesson after this one in the full order, for the "Next lesson" link. */
  next: Lesson | null;
}

type LevelFilter = 'all' | LessonLevel;

/**
 * The Learn tab: short lessons on the stock market and investing, with the terms each one uses
 * explained inside it, and links into the app's other tabs so a client can put what they read into
 * practice.
 *
 * Text comes from learn-content.ts and "read" state from LearnProgress; this page only lays it out.
 */
@Component({
  selector: 'app-learn-page',
  imports: [RouterLink, KeyTerms, LessonText, LessonVisual],
  templateUrl: './learn-page.html',
  styleUrl: './learn-page.css',
})
export class LearnPage {
  protected readonly progress = inject(LearnProgress);

  protected readonly filters: readonly { value: LevelFilter; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'beginner', label: 'Beginner' },
    { value: 'intermediate', label: 'Intermediate' },
  ];
  protected readonly filter = signal<LevelFilter>('all');

  /** Numbered once from the full list, so "3" stays "3" whichever filter is on. */
  private readonly entries: readonly LessonEntry[] = LESSONS.map((lesson, i) => {
    const terms = termsOf(lesson);
    return {
      lesson,
      number: i + 1,
      paragraphs: splitLesson(lesson.body, terms),
      terms,
      next: LESSONS[i + 1] ?? null,
    };
  });

  protected readonly visibleEntries = computed(() => {
    const level = this.filter();
    return level === 'all' ? this.entries : this.entries.filter((e) => e.lesson.level === level);
  });

  /** The lesson a link like /learn#orders points at, which opens instead of staying collapsed. */
  protected readonly targetLesson = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });

  /** Computed so the count follows the progress signal. */
  protected readonly readCount = computed(() => this.progress.countRead(LESSONS.map((l) => l.id)));
  protected readonly total = LESSONS.length;

  /** What to read next, over all lessons so the suggestion does not depend on the filter. */
  protected readonly upNext = computed(() => {
    const id = this.progress.nextUnread(LESSONS.map((l) => l.id));
    return LESSONS.find((l) => l.id === id) ?? null;
  });

  protected setFilter(value: LevelFilter): void {
    this.filter.set(value);
  }

  protected onToggleRead(id: string): void {
    this.progress.toggle(id);
  }
}
