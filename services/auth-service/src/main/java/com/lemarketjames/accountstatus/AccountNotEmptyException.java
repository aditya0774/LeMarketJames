package com.lemarketjames.accountstatus;

/**
 * A client asked to close an account that still holds stocks or has orders in progress; answered
 * as 409 ACCOUNT_NOT_EMPTY. Closing then would leave positions nobody can sell and orders that
 * might still fill on a closed account.
 */
public class AccountNotEmptyException extends RuntimeException {

    public static final String CODE = "ACCOUNT_NOT_EMPTY";

    private final AccountContents.Contents contents;

    public AccountNotEmptyException(AccountContents.Contents contents) {
        super(message(contents));
        this.contents = contents;
    }

    public AccountContents.Contents getContents() {
        return contents;
    }

    private static String message(AccountContents.Contents contents) {
        if (contents.holdings() > 0 && contents.openOrders() > 0) {
            return "Sell your stocks and wait for your open orders to finish before closing your account.";
        }
        return contents.holdings() > 0
                ? "Sell your stocks before closing your account."
                : "Wait for your open orders to finish before closing your account.";
    }
}
