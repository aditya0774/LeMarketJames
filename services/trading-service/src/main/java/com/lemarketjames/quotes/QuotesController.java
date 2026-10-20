package com.lemarketjames.quotes;

import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.quotes.dto.QuoteErrorResponse;
import com.lemarketjames.quotes.dto.QuoteHistoryResponse;
import com.lemarketjames.quotes.dto.QuoteIntradayResponse;
import com.lemarketjames.quotes.dto.QuoteSuccessResponse;
import com.lemarketjames.quotes.dto.QuotesListResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Contract-aligned endpoint for stock quote retrieval.
 */
@RestController
@RequestMapping("/api/quotes")
public class QuotesController {

    private final QuoteService quoteService;
    private final QuoteHistoryService historyService;

    public QuotesController(QuoteService quoteService, QuoteHistoryService historyService) {
        this.quoteService = quoteService;
        this.historyService = historyService;
    }

    /** Every quote in one response, so the dashboard polls once per refresh rather than once per stock. */
    @GetMapping
    public ResponseEntity<?> getAllQuotes() {
        try {
            return ResponseEntity.ok(new QuotesListResponse(true, quoteService.getAllQuotes()));
        } catch (QuoteService.QuoteFeedUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new QuoteErrorResponse(false, ex.getMessage()));
        }
    }

    /**
     * Every stock's prices over today, for the trend graphs. A literal path, so it is matched
     * before {@code /{symbol}}.
     */
    @GetMapping("/intraday")
    public ResponseEntity<?> getIntraday() {
        try {
            return ResponseEntity.ok(new QuoteIntradayResponse(true, historyService.intradayForAll()));
        } catch (QuoteService.QuoteFeedUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new QuoteErrorResponse(false, ex.getMessage()));
        }
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<?> getQuote(@PathVariable String symbol) {
        try {
            return ResponseEntity.ok(new QuoteSuccessResponse(true, quoteService.getQuote(symbol)));
        } catch (QuoteService.SymbolNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new QuoteErrorResponse(false, ex.getMessage()));
        } catch (QuoteService.QuoteFeedUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new QuoteErrorResponse(false, ex.getMessage()));
        }
    }

    /** A stock's price over 1D, 1W, 1M, 3M or 6M, for the trade page's chart. */
    @GetMapping("/{symbol}/history")
    public ResponseEntity<?> getHistory(@PathVariable String symbol,
                                        @RequestParam(defaultValue = "1D") String range) {
        ChartRange chartRange = ChartRange.fromCode(range).orElse(null);
        if (chartRange == null) {
            return ResponseEntity.badRequest().body(new QuoteErrorResponse(false, "Unknown range"));
        }
        try {
            return ResponseEntity.ok(new QuoteHistoryResponse(true, symbol.trim().toUpperCase(Locale.ROOT),
                    chartRange.code(), historyService.history(symbol, chartRange)));
        } catch (QuoteService.SymbolNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new QuoteErrorResponse(false, ex.getMessage()));
        } catch (QuoteService.QuoteFeedUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new QuoteErrorResponse(false, ex.getMessage()));
        }
    }
}
