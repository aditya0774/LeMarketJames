import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ThemeName = 'dark' | 'light';

/** Where the choice is remembered. index.html reads the same key before Angular starts. */
export const THEME_STORAGE_KEY = 'lemarket-theme';

/**
 * The colour theme: dark (purple and gold, the default) or light (blue and red). The palettes
 * live in styles/tokens.css, keyed on <html data-theme>, so switching only flips that attribute.
 *
 * The choice is a per-browser convenience, so it lives in localStorage. Storage can be missing or
 * throw (private windows, blocked site data), and then the app simply stays on dark.
 */
@Injectable({ providedIn: 'root' })
export class Theme {
  private readonly document = inject(DOCUMENT);

  readonly current = signal<ThemeName>(this.readStored());

  constructor() {
    this.apply(this.current());
  }

  set(theme: ThemeName): void {
    this.current.set(theme);
    this.apply(theme);
    try {
      this.document.defaultView?.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Not remembered this time; the theme still applies for this visit.
    }
  }

  private readStored(): ThemeName {
    try {
      return this.document.defaultView?.localStorage.getItem(THEME_STORAGE_KEY) === 'light' ? 'light' : 'dark';
    } catch {
      return 'dark';
    }
  }

  private apply(theme: ThemeName): void {
    this.document.documentElement.dataset['theme'] = theme;
  }
}
