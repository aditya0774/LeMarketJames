import { InsightFlag } from './insight-flag.model';

/** Distinct clients who traded on one day (C6, GET /api/v1/reports/insights/active-clients). */
export interface ActiveClientDay {
  /** YYYY-MM-DD in the reports time zone. */
  date: string;
  activeClients: number;
}

/** The last 30 days, oldest first, with a 0 for every day without trades. Counts only. */
export interface ActiveClientTrendResponse {
  startDate: string;
  endDate: string;
  days: ActiveClientDay[];
  /** The last seven whole days (today left out) and the seven before: what the flag compares. */
  lastWeek: WeekAverage;
  weekBefore: WeekAverage;
  /** Whether the daily average moved enough between the two weeks to be flagged (BR-17). */
  flag: InsightFlag;
  /** ISO instant at which the figures were read. */
  generatedAt: string;
}

/** The average of the daily counts over seven days, both ends included. */
export interface WeekAverage {
  startDate: string;
  endDate: string;
  averageActiveClients: number;
}
