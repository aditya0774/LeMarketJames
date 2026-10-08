package com.lemarketjames.reports.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only entity mapped to the reporting_trades database view.
 * Represents a single settled trade ready for aggregation and reporting.
 *
 * Each row corresponds to one filled order: aggregates account, client, instrument,
 * and order execution data for convenient reporting queries.
 */
@Entity
@Table(name = "reporting_trades")
public class ReportingTrade {

    /**
     * Order identifier (read-only from view; no primary key since this is a view).
     */
    @Id
    @Column(name = "order_id")
    private Integer orderId;

    /**
     * Account ID for the trade.
     */
    @Column(name = "account_id")
    private Integer accountId;

    /**
     * Client ID; parent of the account.
     */
    @Column(name = "client_id")
    private Integer clientId;

    /**
     * Client segment (e.g., "Retail", "Institutional", "VIP").
     */
    @Column(name = "segment")
    private String segment;

    /**
     * Stock ticker symbol (e.g., "AAPL", "GOOG").
     */
    @Column(name = "symbol")
    private String symbol;

    /**
     * Full instrument name (e.g., "Apple Inc").
     */
    @Column(name = "instrument_name")
    private String instrumentName;

    /**
     * Trade side: BUY or SELL.
     */
    @Column(name = "side")
    private String side;

    /**
     * Quantity traded.
     */
    @Column(name = "quantity", precision = 14, scale = 4)
    private BigDecimal quantity;

    /**
     * Execution price per unit.
     */
    @Column(name = "price_per_unit", precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    /**
     * Total trade value: quantity * price_per_unit.
     */
    @Column(name = "gross_amount", precision = 19, scale = 2)
    private BigDecimal grossAmount;

    /**
     * When the trade was submitted.
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * When the trade was filled/executed.
     */
    @Column(name = "filled_at")
    private LocalDateTime filledAt;

    // Getters

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
