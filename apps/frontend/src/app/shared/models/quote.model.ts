export interface Quote {
  symbol: string;
  name: string;
  price: number;
  priceChange: number;
  priceChangePercent: number;
  highPrice: number;
  lowPrice: number;
  openPrice: number;
  volume: number;
  marketCap: number;
  peRatio: number;
  dividendYield: number;
  lastUpdate: string;
}

export interface QuoteSuccessResponse {
  success: true;
  quote: Quote;
}

export interface QuoteErrorResponse {
  success: false;
  error: string;
}
