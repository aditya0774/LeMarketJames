/** The period types the volume insight accepts (C6, GET /api/v1/reports/insights/volume). */
export type VolumeInsightPeriodType = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

/** One whole period's totals. Both dates are included and are days in the reports time zone. */
export interface VolumePeriod {
  startDate: string;
  endDate: string;
  tradeCount: number;
  shareVolume: number;
  grossAmount: number;
}

/** current minus previous. A percent is null when previous is 0 (nothing to compare with). */
export interface VolumeChange {
  shareVolume: number;
  shareVolumePercent: number | null;
  grossAmount: number;
  grossAmountPercent: number | null;
}

export interface VolumeInsightResponse {
  periodType: VolumeInsightPeriodType;
  current: VolumePeriod;
  previous: VolumePeriod;
  /** The period still running, totalled up to today. Not part of `change`. */
  inProgress: VolumePeriod;
  change: VolumeChange;
  /** ISO instant at which the server computed the figures: the "last refreshed" time. */
  generatedAt: string;
}
