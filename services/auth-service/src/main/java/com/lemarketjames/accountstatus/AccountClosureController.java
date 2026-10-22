package com.lemarketjames.accountstatus;

import com.lemarketjames.auth.SessionCookies;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * "Close my account" in the trading app (BR-03, contract C6). CLIENT only (SecurityConfig). It
 * lives under /api/auth because closing ends the session: the reply clears the cookie, as
 * sign-out does.
 */
@RestController
@RequestMapping("/api/auth/close-account")
public class AccountClosureController {

    private final AccountClosureService closures;
    private final SessionCookies cookies;

    public AccountClosureController(AccountClosureService closures, SessionCookies cookies) {
        this.closures = closures;
        this.cookies = cookies;
    }

    /** What is left on the account and whether it may be closed now. */
    @GetMapping
    public AccountContents.Contents check(Authentication client) {
        return closures.check(client.getName());
    }

    /** Closes the account after checking the password, and signs the client out. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> close(@RequestBody CloseAccountRequest request, Authentication client) {
        AccountContents.Contents left = closures.close(client.getName(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(Map.of("message", "Your account is closed.",
                        "cashBalance", left.cashBalance(), "currency", left.currency()));
    }

    /** The client's password, asked again before closing. */
    public record CloseAccountRequest(String password) {
    }
}
