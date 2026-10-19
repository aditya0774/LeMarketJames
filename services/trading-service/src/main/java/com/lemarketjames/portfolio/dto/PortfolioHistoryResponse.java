package com.lemarketjames.portfolio.dto;

import java.util.List;

/**
 * GET /api/v1/portfolio/history response: {"success": true, "range": "1M", "points": [...]}.
 *
 * @param success always true; errors use the shared error body
 * @param range   the requested range code
 * @param points  the value of the account's stocks over the range, oldest first
 */
public record PortfolioHistoryResponse(boolean success, String range, List<PortfolioPoint> points) {
}
