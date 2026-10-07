package com.lemarketjames.reports.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only mapping to the reporting_trades view.
 * This entity is used only for querying aggregate data; it is never persisted.
 */
@Entity
@Table(name = "reporting_trades")
public class ReportingTrade {

    @Id
    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "account_id")
    private Integer accountId;

    @Column(name = "client_id")
    private Integer clientId;

    @Column(name = "segment")
    private String segment;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "instrument_name")
    private String instrumentName;

    @Column(name = "side")
    private String side;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "price_per_unit")
    private BigDecimal pricePerUnit;

    @Column(name = "gross_amount")
    private BigDecimal grossAmount;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "filled_at")
    private LocalDateTime filledAt;

    // Constructors, getters for view mapping

    public ReportingTrade() {
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Integer getClientId() {
        return clientId;
    }

    public String getSegment() {
        return segment;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getFilledAt() {
        return filledAt;
    }
}
