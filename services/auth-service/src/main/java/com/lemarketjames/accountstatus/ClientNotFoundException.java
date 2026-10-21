package com.lemarketjames.accountstatus;

/** No client has the id Trading Ops asked about; answered as 404 CLIENT_NOT_FOUND. */
public class ClientNotFoundException extends RuntimeException {

    public static final String CODE = "CLIENT_NOT_FOUND";

    public ClientNotFoundException() {
        super("Client not found");
    }
}
