package com.lemarketjames.cash;

import java.math.BigDecimal;

/** JSON body shared by the deposit and withdrawal endpoints. */
public record CashMovementRequest(BigDecimal amount, String requestId) {
}
