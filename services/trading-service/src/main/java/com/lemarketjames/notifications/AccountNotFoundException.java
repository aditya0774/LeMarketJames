package com.lemarketjames.notifications;

/** The signed-in user has no trading account, so there is no account to read notifications for. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String username) {
        super("No account for user " + username);
    }
}
