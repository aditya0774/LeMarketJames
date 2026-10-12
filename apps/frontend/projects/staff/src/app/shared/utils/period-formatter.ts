/**
 * Utility to format report period keys into human-readable labels.
 */

/**
 * Format a period key into a human-readable label based on period type.
 *
 * @param period - Period key from API response (e.g., "2026-10-01", "2026-W41", "2026-10", "2026")
 * @param periodType - Type of period: 'DAY', 'WEEK', 'MONTH', or 'YEAR'
 * @returns Human-readable period label (e.g., "October 1, 2026", "Week 41 of 2026")
 */
export function formatPeriod(period: string, periodType: string): string {
  switch (periodType.toUpperCase()) {
    case 'DAY':
      return formatDay(period);
    case 'WEEK':
      return formatWeek(period);
    case 'MONTH':
      return formatMonth(period);
    case 'YEAR':
      return formatYear(period);
    default:
      return period;
  }
}

/**
 * Format daily period: "2026-10-01" → "October 1, 2026"
 */
function formatDay(period: string): string {
  const date = new Date(period + 'T00:00:00Z');
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

/**
 * Format weekly period: "2026-W41" → "Week 41 of 2026 (Oct 5 - Oct 11)"
 */
function formatWeek(period: string): string {
  // Period format: YYYY-Www (ISO 8601 week format)
  const match = period.match(/^(\d{4})-W(\d{2})$/);
  if (!match) {
    return period;
  }

  const year = parseInt(match[1], 10);
  const weekNum = parseInt(match[2], 10);

  // Monday of ISO week 1 is the Monday on or before January 4th (week 1 always contains it).
  // getUTCDay() is 0 for Sunday, but ISO treats Sunday as day 7.
  const jan4 = new Date(Date.UTC(year, 0, 4));
  const jan4IsoDay = jan4.getUTCDay() || 7;
  const monday = new Date(jan4);
  monday.setUTCDate(jan4.getUTCDate() - (jan4IsoDay - 1) + (weekNum - 1) * 7);

  // Sunday of the same week
  const sunday = new Date(monday);
  sunday.setUTCDate(monday.getUTCDate() + 6);

  const mondayStr = monday.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
  const sundayStr = sunday.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });

  return `Week ${weekNum} of ${year} (${mondayStr} - ${sundayStr})`;
}

/**
 * Format monthly period: "2026-10" → "October 2026"
 */
function formatMonth(period: string): string {
  const date = new Date(period + '-01T00:00:00Z');
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  });
}

/**
 * Format yearly period: "2026" → "2026"
 */
function formatYear(period: string): string {
  return period;
}
