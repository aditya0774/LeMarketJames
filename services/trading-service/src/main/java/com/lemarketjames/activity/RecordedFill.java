package com.lemarketjames.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One fill, as far as market activity needs it: which stock, how many shares, at what price and
 * when. The order id is the key, because an order fills once; there is no account, because
 * activity never says who traded. Written once from the event and never changed.
 */
@Entity
@Table(name = "trade_activity")
public class RecordedFill {

    @Id
    private Integer orderId;

    @Column(nullable = false)
    private Integer instrumentId;

    @Column(nullable = false, length = 10)
    private String side;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private Instant filledAt;

    /** For JPA. */
    protected RecordedFill() {
    }

    /** @param fill the fill, as read from Kafka */
    public RecordedFill(OrderFilledMessage fill) {
        this.orderId = fill.orderId();
        this.instrumentId = fill.instrumentId();
        this.side = fill.side();
        this.quantity = fill.quantity();
        this.price = fill.price();
        this.filledAt = fill.filledAt();
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getFilledAt() {
        return filledAt;
    }
}
