import { formatPeriod } from './period-formatter';

describe('formatPeriod', () => {
  it('formats a day as a long date', () => {
    expect(formatPeriod('2026-10-01', 'DAY')).toBe('October 1, 2026');
  });

  it('formats a month with its year', () => {
    expect(formatPeriod('2026-10', 'MONTH')).toBe('October 2026');
  });

  it('returns a year unchanged', () => {
    expect(formatPeriod('2026', 'YEAR')).toBe('2026');
  });

  it('accepts the period type in any case', () => {
    expect(formatPeriod('2026-10', 'month')).toBe('October 2026');
  });

  it('returns the key unchanged for an unknown period type', () => {
    expect(formatPeriod('whatever', 'DECADE')).toBe('whatever');
  });

  describe('weeks', () => {
    it('shows the Monday to Sunday range of an ISO week', () => {
      expect(formatPeriod('2026-W41', 'WEEK')).toBe('Week 41 of 2026 (Oct 5 - Oct 11)');
    });

    it('starts week 1 in the previous year when January 4th is a Sunday', () => {
      expect(formatPeriod('2026-W01', 'WEEK')).toBe('Week 1 of 2026 (Dec 29 - Jan 4)');
    });

    it('starts week 1 on January 4th when it is a Monday', () => {
      expect(formatPeriod('2021-W01', 'WEEK')).toBe('Week 1 of 2021 (Jan 4 - Jan 10)');
    });

    it('returns a malformed week key unchanged', () => {
      expect(formatPeriod('2026-41', 'WEEK')).toBe('2026-41');
    });
  });
});
