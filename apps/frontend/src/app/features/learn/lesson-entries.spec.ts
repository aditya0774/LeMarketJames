import { LESSONS } from './learn-content';
import { entryFor, LESSON_ENTRIES } from './lesson-entries';

describe('LESSON_ENTRIES', () => {
  it('has one entry per lesson, numbered from 1 in order', () => {
    expect(LESSON_ENTRIES.map((e) => e.lesson.id)).toEqual(LESSONS.map((l) => l.id));
    expect(LESSON_ENTRIES.map((e) => e.number)).toEqual(LESSONS.map((_, i) => i + 1));
  });

  it('links each lesson to its neighbours, with nothing before the first or after the last', () => {
    expect(LESSON_ENTRIES[0].prev).toBeNull();
    expect(LESSON_ENTRIES[0].next).toBe(LESSONS[1]);
    expect(LESSON_ENTRIES[3].prev).toBe(LESSONS[2]);
    expect(LESSON_ENTRIES[3].next).toBe(LESSONS[4]);
    expect(LESSON_ENTRIES[LESSONS.length - 1].next).toBeNull();
  });

  it('keeps every paragraph of the lesson, with its terms marked', () => {
    for (const entry of LESSON_ENTRIES) {
      expect(entry.paragraphs.map((segs) => segs.map((s) => s.text).join(''))).toEqual(entry.lesson.body);
      expect(entry.terms.map((t) => t.term)).toEqual(entry.lesson.terms);
    }
  });
});

describe('entryFor', () => {
  it('finds a lesson by id', () => {
    expect(entryFor('orders')?.lesson.id).toBe('orders');
  });

  it('returns undefined for an unknown, empty or missing id', () => {
    expect(entryFor('nope')).toBeUndefined();
    expect(entryFor('')).toBeUndefined();
    expect(entryFor(null)).toBeUndefined();
  });
});
