import { InsightFlag } from './insight-flag.model';

export interface MostActiveStock {
  rank: number;
  symbol: string;
  tradedValue: number;
  tradeCount: number;
  shareVolume: number;
  /** The stock's traded value over the same days of last week; 0 when it did not trade. */
  previousTradedValue: number;
  /** Whether `tradedValue` moved enough against it to be flagged (BR-17). */
  flag: InsightFlag;
}

/** This week so far, each stock compared with the same days of last week (C6). */
export interface MostActiveStocksResponse {
  startDate: string;
  endDate: string;
  previousStartDate: string;
  previousEndDate: string;
  stocks: MostActiveStock[];
  generatedAt: string;
}
