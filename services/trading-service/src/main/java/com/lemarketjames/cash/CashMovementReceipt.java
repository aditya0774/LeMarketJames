package com.lemarketjames.cash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Durable outcome of one request, committed atomically with the account and audit event. */
@Entity
@Table(name = "cash_movement_receipts")
public class CashMovementReceipt {

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 10)
    private CashMovementType movementType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "cash_balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CashMovementReceipt() {
        // for JPA
    }

    public CashMovementReceipt(UUID requestId, Integer accountId, CashMovementType movementType,
                               BigDecimal amount, BigDecimal cashBalanceAfter, Instant createdAt) {
        this.requestId = requestId;
        this.accountId = accountId;
        this.movementType = movementType;
        this.amount = amount;
        this.cashBalanceAfter = cashBalanceAfter;
        this.createdAt = createdAt;
    }

    public BigDecimal getCashBalanceAfter() {
        return cashBalanceAfter;
    }

    public Integer getAccountId() {
        return accountId;
    }
}
