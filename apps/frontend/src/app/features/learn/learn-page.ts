import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LearnProgress } from './learn-progress';
import { LessonLevel, LESSONS } from './learn-content';
import { LESSON_ENTRIES } from './lesson-entries';

type LevelFilter = 'all' | LessonLevel;

/**
 * The Learn tab's index: where to start or continue, a level filter and a tile per lesson. Each
 * tile opens that lesson in the reader (`/learn/:lessonId`), which has the text, Back and Next.
 *
 * What to show comes from lesson-entries.ts and "read" state from LearnProgress; this page only
 * lays it out.
 */
@Component({
  selector: 'app-learn-page',
  imports: [RouterLink],
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

  /** Numbered from the full list, so "3" stays "3" whichever filter is on. */
  protected readonly visibleEntries = computed(() => {
    const level = this.filter();
    return level === 'all' ? LESSON_ENTRIES : LESSON_ENTRIES.filter((e) => e.lesson.level === level);
  });

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
}
