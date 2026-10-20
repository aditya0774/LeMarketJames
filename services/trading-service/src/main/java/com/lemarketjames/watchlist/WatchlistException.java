package com.lemarketjames.watchlist;

/** A watchlist refusal with the stable code contract C6 gives it. */
public class WatchlistException extends RuntimeException {

    private final String code;

    public WatchlistException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
