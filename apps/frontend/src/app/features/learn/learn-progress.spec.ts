import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { LearnProgress } from './learn-progress';

describe('LearnProgress', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem('learn.read');
  });

  const service = () => TestBed.inject(LearnProgress);

  it('suggests the first lesson when nothing is read', () => {
    expect(service().nextUnread(['a', 'b', 'c'])).toBe('a');
  });

  it('suggests the first unread lesson in order, not the first after the last read', () => {
    const progress = service();
    progress.toggle('a');
    progress.toggle('c');

    expect(progress.nextUnread(['a', 'b', 'c'])).toBe('b');
  });

  it('returns null when every lesson is read', () => {
    const progress = service();
    progress.toggle('a');
    progress.toggle('b');

    expect(progress.nextUnread(['a', 'b'])).toBeNull();
  });

  it('ignores read ids that are no longer lessons', () => {
    localStorage.setItem('learn.read', JSON.stringify(['gone']));

    const progress = service();

    expect(progress.countRead(['a', 'b'])).toBe(0);
    expect(progress.nextUnread(['a', 'b'])).toBe('a');
  });

  it('starts empty when the saved value is not a list', () => {
    localStorage.setItem('learn.read', '{"a":1}');

    expect(service().nextUnread(['a'])).toBe('a');
  });

  it('keeps working when storage cannot be read or written', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    const progress = service();
    progress.toggle('a');

    expect(progress.isRead('a')).toBe(true);
  });
});
