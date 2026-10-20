package com.lemarketjames;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The root greeting, kept from core-service's former main class: CI's smoke test reads it to
 * check that trading-service answers at all, without a session.
 */
@RestController
public class HomeController {

    /**
     * Provides a simple greeting endpoint.
     *
     * @return a greeting message from LeMarketJames
     */
    @GetMapping("/")
    public String greeting() {
        return Greeter.greeting();
    }
}
