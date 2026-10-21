/**
 * Whether an insight moved enough against the period it is compared with to be flagged (BR-17,
 * C6). The server decides, by the `lmj.reports.insight-flag-percent` setting (C5), so the app keeps
 * no threshold of its own.
 */
export interface InsightFlag {
  flagged: boolean;
  /** The move as a percent of the previous period; null when the previous period had none. */
  changePercent: number | null;
  direction: 'UP' | 'DOWN' | 'FLAT';
  /** The setting in force: how far an insight must move to be flagged. */
  thresholdPercent: number;
}
