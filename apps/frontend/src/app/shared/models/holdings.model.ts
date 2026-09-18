/**
 * Holdings Domain Models
 * 
 * Aligns with backend Spring Boot DTOs in com.lemarketjames.holdings.dto
 * 
 * AC1: Unauthorized data access prevented
 * AC2: Requests scoped to authenticated user
 */

/**
 * Represents a single stock holding position
 * 
 * Contains quantity, cost basis, and current valuation metrics.
 * All monetary values are in USD as BigDecimal on backend (mapped to number here).
 */
export interface HoldingDto {
  /** Stock symbol (e.g., "AAPL") */
  symbol: string;
  
  /** Quantity of shares held */
  quantity: number;
  
  /** Average purchase price per share */
  averageCost: number;
  
  /** Current market price per share (from quotes service) */
  currentPrice: number;
  
  /** Total cost basis (quantity × averageCost) */
  totalCost: number;
  
  /** Current market value (quantity × currentPrice) */
  currentValue: number;
  
  /** Unrealized gain/loss in USD (currentValue - totalCost) */
  gainLoss: number;
  
  /** Unrealized gain/loss as percentage ((gainLoss / totalCost) × 100) */
  gainLossPercent: number;
}

/**
 * Response from GET /api/v1/holdings
 * 
 * AC1: Returns only user's own holdings (authorization validated on backend)
 * AC2: Scoped to authenticated user's account
 */
export interface HoldingsResponse {
  /** Success flag indicating if holdings were retrieved */
  success: boolean;
  
  /** Array of holdings for the authenticated user's account */
  holdings: HoldingDto[];
  
  /** Optional success message (e.g., "Holdings retrieved successfully") */
  message?: string;
}

/**
 * Request for POST /api/v1/holdings/validate
 * 
 * Validates that user has sufficient holdings before placing a SELL order.
 * AccountId is automatically populated by HoldingsService (not required from caller).
 * 
 * AC2: Requests scoped to authenticated user
 */
export interface ValidateHoldingRequest {
  /** Account ID (populated by service from Auth context) */
  accountId: number;
  
  /** Instrument/stock ID to validate holdings for */
  instrumentId: number;
  
  /** Quantity attempting to be sold */
  sellQuantity: number;
}

/**
 * Response from POST /api/v1/holdings/validate
 * 
 * AC1: Returns 403 if user doesn't own account (with ACCOUNT_ACCESS_DENIED code)
 * AC1: Returns 400 if insufficient holdings (with INSUFFICIENT_HOLDINGS code)
 * AC2: Safe error messages that don't leak data
 */
export interface ValidateHoldingResponse {
  /** Success flag */
  success: boolean;
  
  /** Success message (e.g., "Sufficient holdings available") */
  message?: string;
  
  /** Error message if validation failed (safe, user-facing text) */
  error?: string;
  
  /** Error code for programmatic handling:
   *  - ACCOUNT_ACCESS_DENIED (403): User doesn't own this account
   *  - INSUFFICIENT_HOLDINGS (400): Not enough shares to sell
   *  - UNAUTHORIZED (401): Authentication required
   *  - FORBIDDEN (403): Generic access denied
   *  - SERVER_ERROR (500+): Backend error
   */
  code?: string;
}
