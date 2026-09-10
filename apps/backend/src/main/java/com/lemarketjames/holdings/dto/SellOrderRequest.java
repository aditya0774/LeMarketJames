package com.lemarketjames.holdings.dto;

import java.math.BigDecimal;

/**
 * Data transfer object for sell order requests.
 * Contains the information needed to validate and execute a sell order.
 */
public class SellOrderRequest {
    private Long accountId;
    private Long instrumentId;
    private BigDecimal sellQuantity;

    /**
     * Default constructor for deserialization.
     */
    public SellOrderRequest() {
    }

    /**
     * Constructs a SellOrderRequest with the provided information.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @param sellQuantity the quantity to sell
     */
    public SellOrderRequest(Long accountId, Long instrumentId, BigDecimal sellQuantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.sellQuantity = sellQuantity;
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
     * Gets the sell quantity.
     *
     * @return the quantity to sell
     */
    public BigDecimal getSellQuantity() {
        return sellQuantity;
    }

    /**
     * Sets the sell quantity.
     *
     * @param sellQuantity the quantity to sell
     */
    public void setSellQuantity(BigDecimal sellQuantity) {
        this.sellQuantity = sellQuantity;
    }
}
