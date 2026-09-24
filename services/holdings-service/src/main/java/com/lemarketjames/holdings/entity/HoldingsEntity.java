package com.lemarketjames.holdings.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "holdings")
public class HoldingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer holdingId;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    // Quantity-weighted average price paid per share, updated on every settled BUY (see
    // HoldingsSettlementService). Zero for holdings that predate settlement (seed data).
    @Column(name = "average_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageCost;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    public HoldingsEntity() {}

    public HoldingsEntity(Integer accountId, Integer instrumentId, BigDecimal quantity) {
        this(accountId, instrumentId, quantity, BigDecimal.ZERO);
    }

    public HoldingsEntity(Integer accountId, Integer instrumentId, BigDecimal quantity, BigDecimal averageCost) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.averageCost = averageCost;
        this.lastUpdated = LocalDateTime.now();
    }

    public Integer getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Integer holdingId) {
        this.holdingId = holdingId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public boolean canSell(BigDecimal sellQuantity) {
        return sellQuantity != null && sellQuantity.compareTo(quantity) <= 0;
    }
}
