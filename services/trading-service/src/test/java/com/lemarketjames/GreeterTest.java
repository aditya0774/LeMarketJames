package com.lemarketjames;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Greeter utility class.
 */
class GreeterTest {
    /**
     * Tests that the greeting method returns the expected message.
     */
    @Test
    void returnsExpectedGreeting() {
        assertEquals("Hello from LeMarketJames!", Greeter.greeting());
    }
}