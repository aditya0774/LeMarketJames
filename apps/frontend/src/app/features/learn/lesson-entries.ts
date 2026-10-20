import { GlossaryTerm, Lesson, LESSONS } from './learn-content';
import { splitLesson, termsOf, TextSegment } from './term-links';

/** A lesson with everything the Learn pages need to show it, built once from the content. */
export interface LessonEntry {
  lesson: Lesson;
  /** Its place in the suggested reading order; a filter never changes it. */
  number: number;
  /** The body paragraphs with the lesson's glossary terms marked, ready to show. */
  paragraphs: TextSegment[][];
  /** The terms the lesson teaches, shown as clickable chips in its key terms box. */
  terms: GlossaryTerm[];
  /** The neighbours in the full order, for Back and Next. */
  prev: Lesson | null;
  next: Lesson | null;
}

/**
 * Every lesson as an entry. Built in one place so the index and the reader number lessons the
 * same way and mark the same terms, rather than each page working it out itself.
 */
export const LESSON_ENTRIES: readonly LessonEntry[] = LESSONS.map((lesson, i) => {
  const terms = termsOf(lesson);
  return {
    lesson,
    number: i + 1,
    paragraphs: splitLesson(lesson.body, terms),
    terms,
    prev: LESSONS[i - 1] ?? null,
    next: LESSONS[i + 1] ?? null,
  };
});

/** The entry for a lesson id, or undefined when there is no such lesson. */
export function entryFor(id: string | null | undefined): LessonEntry | undefined {
  return LESSON_ENTRIES.find((e) => e.lesson.id === id);
}
