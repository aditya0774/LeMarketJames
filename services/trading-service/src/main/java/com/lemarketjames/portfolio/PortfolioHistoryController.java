package com.lemarketjames.portfolio;

import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.portfolio.dto.PortfolioHistoryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * The overview chart's data: what the caller's stocks were worth over 1D, 1W, 1M, 3M or 6M. Scoped
 * from the JWT like {@link PortfolioController}, so there is no accountId parameter.
 */
@RestController
public class PortfolioHistoryController {

    private final PortfolioHistoryService historyService;

    public PortfolioHistoryController(PortfolioHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/api/v1/portfolio/history")
    public ResponseEntity<?> getHistory(@RequestParam(defaultValue = "1D") String range) {
        Optional<ChartRange> chartRange = ChartRange.fromCode(range);
        if (chartRange.isEmpty()) {
            // Answered here rather than thrown: HoldingsExceptionHandler only maps the account-not-found
            // IllegalArgumentException.
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Unknown range"));
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(new PortfolioHistoryResponse(true, chartRange.get().code(),
                historyService.history(username, chartRange.get())));
    }
}
