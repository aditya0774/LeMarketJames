import { VolumeRangeRequest } from '../models/volume-insight.model';

/** A preset the tile always shows: the range being looked at against the one it is compared with. */
export interface VolumePreset {
  key: 'TODAY' | 'THIS_WEEK' | 'LAST_WEEK';
  label: string;
  request: VolumeRangeRequest;
}

/** A day as YYYY-MM-DD from the browser's calendar day (not UTC, which can already be tomorrow). */
export function toIsoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function addDays(date: Date, days: number): Date {
  const moved = new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
  return moved;
}

/** The Monday of the week containing the day (weeks run Monday to Sunday, as in the reports). */
export function mondayOf(date: Date): Date {
  const sinceMonday = (date.getDay() + 6) % 7;
  return addDays(date, -sinceMonday);
}

/**
 * The calendar day it is at `now` in a time zone, as a local Date at midnight: the day arithmetic
 * here treats local Dates as plain calendar days. Without a zone, the browser's own day.
 *
 * Reports count days in the reports time zone (C5), so "today" must be that zone's day: late in the
 * evening in New York, a browser on UTC is already on tomorrow, which has no trades yet.
 */
export function calendarDayIn(now: Date, timeZone?: string): Date {
  if (!timeZone) {
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }
  const parts = new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: 'numeric', day: 'numeric' })
    .formatToParts(now);
  const part = (type: 'year' | 'month' | 'day') => Number(parts.find((p) => p.type === type)!.value);
  return new Date(part('year'), part('month') - 1, part('day'));
}

/**
 * Today, this week so far and last week, each against the period just before it. The week so far is
 * compared with the same number of days of last week, so a Tuesday is not set against a full week.
 *
 * @param timeZone the reports time zone; "today" is its day. Omitted, the browser's day is used.
 */
export function volumePresets(now: Date, timeZone?: string): VolumePreset[] {
  const today = calendarDayIn(now, timeZone);
  const monday = mondayOf(today);
  const lastMonday = addDays(monday, -7);
  const iso = toIsoDay;

  return [
    {
      key: 'TODAY',
      label: 'Today',
      request: {
        currentFrom: iso(today), currentTo: iso(today),
        previousFrom: iso(addDays(today, -1)), previousTo: iso(addDays(today, -1)),
      },
    },
    {
      key: 'THIS_WEEK',
      label: 'This week',
      request: {
        currentFrom: iso(monday), currentTo: iso(today),
        previousFrom: iso(lastMonday), previousTo: iso(addDays(today, -7)),
      },
    },
    {
      key: 'LAST_WEEK',
      label: 'Last week',
      request: {
        currentFrom: iso(lastMonday), currentTo: iso(addDays(monday, -1)),
        previousFrom: iso(addDays(lastMonday, -7)), previousTo: iso(addDays(monday, -8)),
      },
    },
  ];
}
