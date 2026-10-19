/**
 * Formats a UTC instant as a time in the reports time zone, e.g. "Oct 14, 2026, 2:04 AM EDT".
 *
 * Report times follow the reports time zone (C5, lmj.reports.time-zone), not the browser's.
 * Angular's DatePipe only takes fixed offsets, which would be wrong across a daylight-saving
 * change, so this uses Intl with the IANA zone the server sends.
 */
export function formatReportTime(isoUtc: string, timeZone: string): string {
  return new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  }).format(new Date(isoUtc));
}
