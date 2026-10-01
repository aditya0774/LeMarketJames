package com.lemarketjames.quotes;

import com.lemarketjames.quotes.dto.QuoteErrorResponse;
import com.lemarketjames.quotes.dto.QuoteSuccessResponse;
import com.lemarketjames.quotes.dto.QuotesListResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract-aligned endpoint for stock quote retrieval.
 */
@RestController
@RequestMapping("/api/quotes")
public class QuotesController {

    private final QuoteService quoteService;

    public QuotesController(QuoteService quoteService) {
        this.quoteService = quoteService;
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
}
