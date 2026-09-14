// Order and Balance interfaces aligned with API contracts

export type OrderType = 'BUY' | 'SELL';
export type OrderTypeValue = 'MARKET' | 'LIMIT';
export type OrderStatus = 'PENDING' | 'EXECUTED' | 'CANCELLED';

export interface Order {
  orderId: string;
  symbol: string;
  quantity: number;
  type: OrderType;
  orderType: OrderTypeValue;
  price: number;
  status: OrderStatus;
  createdAt: string;
  executedAt: string | null;
}

export interface OrderRequest {
  symbol: string;
  quantity: number;
  type: OrderType;
  orderType: OrderTypeValue;
  price: number;
}

export interface OrderResponse {
  success: boolean;
  orderId?: string;
  symbol?: string;
  quantity?: number;
  type?: OrderType;
  orderType?: OrderTypeValue;
  price?: number;
  status?: OrderStatus;
  createdAt?: string;
  executedAt?: string | null;
  error?: string;
  code?: string;
}

export interface BalanceInfo {
  cash: number;
  invested: number;
  totalValue: number;
  buyingPower: number;
  dayGainLoss: number;
  dayGainLossPercent: number;
  totalGainLoss: number;
  totalGainLossPercent: number;
  currency: string;
}

export interface BalanceResponse {
  success: boolean;
  balance?: BalanceInfo;
  error?: string;
}

export interface OrdersListResponse {
  success: boolean;
  orders?: Order[];
  total?: number;
  limit?: number;
  offset?: number;
  error?: string;
}

export interface OrderDetailsResponse {
  success: boolean;
  order?: Order;
  error?: string;
}
