package com.lemarketjames.orders.submission;

import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * The server-generated ID of one order submission (contract C2). Every audit event of the
 * submission carries it, and it is the only identifier a refused order has. The server issues it
 * so a caller can't choose or reuse one.
 */
public final class SubmissionRequestId {

    public static final String HEADER = "X-Request-Id";

    private SubmissionRequestId() {
    }

    /**
     * Issues a new ID and puts it on the response straight away, so the caller receives it however
     * the submission ends: accepted, refused with a response, or refused by an exception handler.
     */
    public static String issue(HttpServletResponse response) {
        String requestId = UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        return requestId;
    }
}
