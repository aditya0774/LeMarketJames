package com.lemarketjames.holdings;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing a user's current holding (position) in an instrument.
 * Maps to the 'holdings' table in PostgreSQL.
 */
@Entity
@Table(name = "holdings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"account_id", "instrument_id"})
})
public class Holdings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    /**
     * Default constructor for JPA.
     */
    public Holdings() {
    }

    /**
     * Constructs a Holdings entity with the provided information.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @param quantity the quantity held
     */
    public Holdings(Long accountId, Long instrumentId, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.lastUpdated = Instant.now();
    }

    /**
     * Gets the holding ID.
     *
     * @return the holding ID
     */
    public Long getHoldingId() {
        return holdingId;
    }

    /**
     * Sets the holding ID.
     *
     * @param holdingId the holding ID to set
     */
    public void setHoldingId(Long holdingId) {
        this.holdingId = holdingId;
    }

    /**
     * Gets the account ID.
     *
     * @return the account ID
     */
    public Long getAccountId() {
        return accountId;
    }

    /**
     * Sets the account ID.
     *
     * @param accountId the account ID to set
     */
    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    /**
     * Gets the instrument ID.
     *
     * @return the instrument ID
     */
    public Long getInstrumentId() {
        return instrumentId;
    }

    /**
     * Sets the instrument ID.
     *
     * @param instrumentId the instrument ID to set
     */
    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    /**
     * Gets the quantity held.
     *
     * @return the quantity
     */
    public BigDecimal getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity held.
     *
     * @param quantity the quantity to set
     */
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    /**
     * Gets the last updated timestamp.
     *
     * @return the last updated timestamp
     */
    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the last updated timestamp.
     *
     * @param lastUpdated the timestamp to set
     */
    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
