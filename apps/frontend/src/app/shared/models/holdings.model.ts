export interface HoldingDto {
  symbol: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  totalCost: number;
  currentValue: number;
  gainLoss: number;
  gainLossPercent: number;
}

export interface HoldingsResponse {
  success: boolean;
  holdings: HoldingDto[];
  message?: string;
}

export interface ValidateHoldingRequest {
  accountId: number;
  instrumentId: number;
  sellQuantity: number;
}

export interface ValidateHoldingResponse {
  success: boolean;
  message?: string;
  error?: string;
  code?: string;
}
