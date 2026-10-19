export interface MostActiveStock {
  rank: number;
  symbol: string;
  tradedValue: number;
  tradeCount: number;
  shareVolume: number;
}

export interface MostActiveStocksResponse {
  startDate: string;
  endDate: string;
  stocks: MostActiveStock[];
  generatedAt: string;
}