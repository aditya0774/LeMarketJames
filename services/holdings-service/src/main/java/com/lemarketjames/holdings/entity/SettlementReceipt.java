package com.lemarketjames.holdings.entity;

import com.lemarketjames.holdings.dto.SettlementRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** One durable result per order, committed atomically with its cash and share changes. */
@Entity
@Table(name = "settlement_receipts")
public class SettlementReceipt {
    @Id private Integer orderId;
    @Column(nullable = false) private Integer accountId;
    @Column(nullable = false) private Integer instrumentId;
    @Column(nullable = false, length = 10) private String orderType;
    @Column(nullable = false, precision = 14, scale = 4) private BigDecimal quantity;
    @Column(nullable = false, precision = 14, scale = 4) private BigDecimal pricePerUnit;
    @Column(length = 255) private String rejectionReason;
    protected SettlementReceipt() {}
    public SettlementReceipt(SettlementRequest request, String rejectionReason) {
        orderId = request.getOrderId(); accountId = request.getAccountId(); instrumentId = request.getInstrumentId();
        orderType = request.getOrderType().name(); quantity = request.getQuantity();
        pricePerUnit = request.getPricePerUnit(); this.rejectionReason = rejectionReason;
    }
    public String getRejectionReason() { return rejectionReason; }
    public boolean matches(SettlementRequest request) {
        return accountId.equals(request.getAccountId()) && instrumentId.equals(request.getInstrumentId())
            && orderType.equals(request.getOrderType().name()) && quantity.compareTo(request.getQuantity()) == 0
            && pricePerUnit.compareTo(request.getPricePerUnit()) == 0;
    }
}
