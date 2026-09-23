package com.lemarketjames;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the auth microservice (registration, login, logout, /me).
 * Lives in the root package so component, entity, and repository scanning also picks up the
 * shared beans in com.lemarketjames.common (JWT, account domain, error handling).
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
