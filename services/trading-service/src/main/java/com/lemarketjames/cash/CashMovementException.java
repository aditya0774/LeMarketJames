package com.lemarketjames.cash;

/** A cash movement refusal with the stable code exposed by contract C6. */
public class CashMovementException extends RuntimeException {

    private final String code;

    public CashMovementException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
