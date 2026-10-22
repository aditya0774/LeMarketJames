/**
 * Where an overnight run is (C6, GET /api/v1/reports/overnight). LATE: not ready by readyBy, and
 * either still going (readyAt null) or finished after it (readyAt set).
 */
export type OvernightReportStatus = 'SCHEDULED' | 'RUNNING' | 'READY' | 'LATE' | 'FAILED';

/** One overnight run of one report. Instants are ISO-8601 UTC; the page shows them in the reports time zone. */
export interface OvernightReport {
  id: string;
  name: string;
  /** The trading day the run covers, YYYY-MM-DD in the reports time zone. */
  runDate: string;
  status: OvernightReportStatus;
  scheduledFor: string;
  /** When a successful run is available by (configurable on the server, 07:00 by default). */
  readyBy: string;
  /** When it finished: set when READY, and when LATE once the late run has finished. */
  readyAt: string | null;
  /** Set only when FAILED: the platform sends no notifications, so this is how an analyst learns why. */
  failureReason: string | null;
  /** The file a finished run produced (BR-16), downloaded from OvernightReportsService.outputUrl; null when none. */
  outputFileName: string | null;
}

export interface OvernightReportsResponse {
  success: boolean;
  /** IANA ID of the reports time zone, e.g. America/New_York. */
  timeZone: string;
  /** Newest runDate first. */
  data: OvernightReport[];
}
