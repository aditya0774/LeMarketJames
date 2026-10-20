package com.lemarketjames.holdings.stream;

import com.lemarketjames.common.domain.AccountRepository;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The signed-in client's live portfolio stream (contract C6). There is no account in the path:
 * the account is the caller's own, so nobody can listen to another client's settlements.
 */
@RestController
public class PortfolioStreamController {

    private final AccountRepository accounts;
    private final PortfolioStreamService streams;

    public PortfolioStreamController(AccountRepository accounts, PortfolioStreamService streams) {
        this.accounts = accounts;
        this.streams = streams;
    }

    /**
     * Opens a {@code balance-changed} stream for the caller's own account. A login without an
     * account gets the same 404 holdings gives it (HoldingsExceptionHandler).
     */
    @GetMapping(value = "/api/v1/portfolio/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication caller) {
        Integer accountId = accounts.findAccountIdByUsername(caller.getName())
            .orElseThrow(() -> new IllegalArgumentException("No account found for username: " + caller.getName()));
        return streams.subscribe(accountId);
    }
}
