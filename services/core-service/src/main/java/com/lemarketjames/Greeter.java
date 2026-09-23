package com.lemarketjames;

/**
 * Simple utility class providing greeting functionality.
 */
public class Greeter {
    /**
     * Returns a greeting message.
     *
     * @return a greeting message from LeMarketJames
     */
    public static String greeting() {
        return "Hello from LeMarketJames!";
    }

    /**
     * Main method that prints a greeting message to the console.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println(greeting());
    }
}