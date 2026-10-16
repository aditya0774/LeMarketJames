import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, Theme } from './theme';

describe('Theme', () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset['theme'];
  });

  afterEach(() => vi.restoreAllMocks());

  it('starts on dark when nothing is stored', () => {
    const theme = TestBed.inject(Theme);

    expect(theme.current()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('starts on the stored choice', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    expect(TestBed.inject(Theme).current()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('applies and remembers a new choice', () => {
    const theme = TestBed.inject(Theme);

    theme.set('light');

    expect(theme.current()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('still switches when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const theme = TestBed.inject(Theme);

    expect(theme.current()).toBe('dark');
    theme.set('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });
});
