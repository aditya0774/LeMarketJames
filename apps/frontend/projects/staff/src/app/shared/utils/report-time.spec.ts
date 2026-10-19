import { formatReportTime } from './report-time';

describe('formatReportTime', () => {
  // \s also matches the narrow no-break space some ICU versions put before AM/PM.
  it('shows a summer instant in New York daylight time', () => {
    expect(formatReportTime('2026-10-14T06:04:00Z', 'America/New_York')).toMatch(/^Oct 14, 2026, 2:04\sAM EDT$/);
  });

  it('shows a winter instant in New York standard time', () => {
    expect(formatReportTime('2026-12-01T12:00:00Z', 'America/New_York')).toMatch(/^Dec 1, 2026, 7:00\sAM EST$/);
  });

  it('follows the zone it is given, not the browser', () => {
    expect(formatReportTime('2026-10-14T06:04:00Z', 'UTC')).toMatch(/^Oct 14, 2026, 6:04\sAM UTC$/);
  });
});
