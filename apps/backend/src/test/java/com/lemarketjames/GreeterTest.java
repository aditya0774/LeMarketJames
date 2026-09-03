package com.lemarketjames;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreeterTest {
    @Test
    void returnsExpectedGreeting() {
        assertEquals("Hello from LeMarketJames!", Greeter.greeting());
    }
}