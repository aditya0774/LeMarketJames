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
  /** ISO instant at which the figures were read. */
  generatedAt: string;
}
