package com.lemarketjames.holdings.client;
import com.lemarketjames.orders.entity.RejectionReason;
/** A definite, persisted refusal; transport failures must never be treated as rejection. */
public class SettlementRejectedException extends RuntimeException {
    private final RejectionReason reason;
    public SettlementRejectedException(RejectionReason reason) { super(reason.name()); this.reason = reason; }
    public RejectionReason getReason() { return reason; }
}
