// Trade data model aligned with C6 API contract

export type TradeSide = 'BUY' | 'SELL';

export interface TradeDto {
  symbol: string;
  side: TradeSide;
  quantity: number;
  pricePerUnit: number;
  filledAt: string; // ISO timestamp (UTC)
}

export interface TradeFilterState {
  selectedSymbol: string | null;
  sortColumn: keyof TradeDto | null;
  sortAscending: boolean;
}
