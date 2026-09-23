package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Main application class for the LeMarketJames Spring Boot application.
 * Serves as the entry point and also provides a basic greeting endpoint.
 */
@SpringBootApplication
@RestController
public class Main {

    /**
     * Main entry point for the Spring Boot application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    /**
     * Provides a simple greeting endpoint.
     *
     * @return a greeting message from LeMarketJames
     */
    @GetMapping("/")
    public String greeting() {
        return "Hello from LeMarketJames!";
    }
}
