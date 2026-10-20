import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'learn.read';

/**
 * Which lessons this browser has marked as read. It is a per-device convenience, so it lives in
 * localStorage and the page works the same when storage is blocked or empty.
 */
@Injectable({ providedIn: 'root' })
export class LearnProgress {
  private readonly readIds = signal<ReadonlySet<string>>(this.load());

  isRead(id: string): boolean {
    return this.readIds().has(id);
  }

  toggle(id: string): void {
    const next = new Set(this.readIds());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.readIds.set(next);
    this.save(next);
  }

  /** Marks a lesson read; unlike toggle, doing it again leaves it read. */
  markRead(id: string): void {
    if (this.isRead(id)) {
      return;
    }
    const next = new Set(this.readIds()).add(id);
    this.readIds.set(next);
    this.save(next);
  }

  /** How many of the given lessons are read, ignoring ids that no longer exist. */
  countRead(ids: readonly string[]): number {
    const read = this.readIds();
    return ids.filter((id) => read.has(id)).length;
  }

  /** The first of the given lessons, in order, that is not read yet; null when all are read. */
  nextUnread(ids: readonly string[]): string | null {
    const read = this.readIds();
    return ids.find((id) => !read.has(id)) ?? null;
  }

  private load(): ReadonlySet<string> {
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      return new Set(Array.isArray(raw) ? raw.filter((v): v is string => typeof v === 'string') : []);
    } catch {
      return new Set();
    }
  }

  private save(ids: ReadonlySet<string>): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify([...ids]));
    } catch {
      // Storage is blocked or full: progress just lasts until the page is closed.
    }
  }
}
