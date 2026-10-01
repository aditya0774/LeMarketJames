package com.lemarketjames.holdings.service;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.dto.SettlementRequest;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Applies the cash and holdings side effects of a fill. This is the only place in the system that
 * writes to holdings or debits/credits cash — order placement (buy-sell-service) only validates and
 * records the order; nothing is settled until this runs.
 *
 * <p>Order placement's cash check is a point-in-time check and can't account for other orders
 * placed in the meantime, so the cash/quantity checks here are re-validated defensively rather than
 * trusted from buy-sell-service. This is not a full reservation/holds system: two orders placed before
 * either fills can still both pass their placement-time check and one settlement can fail here.
 */
@Service
public class HoldingsSettlementService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsSettlementService.class);

    private final HoldingsRepository holdingsRepository;
    private final AccountRepository accountRepository;
    private final AuditRecorder auditRecorder;
    private final com.lemarketjames.holdings.repository.SettlementReceiptRepository receipts;

    public HoldingsSettlementService(HoldingsRepository holdingsRepository, AccountRepository accountRepository,
                                     AuditRecorder auditRecorder, com.lemarketjames.holdings.repository.SettlementReceiptRepository receipts) {
        this.holdingsRepository = holdingsRepository;
        this.accountRepository = accountRepository;
        this.auditRecorder = auditRecorder;
        this.receipts = receipts;
    }

    /**
     * Applies the fill and records it as SETTLED in the same transaction (contract C2), so the audit
     * trail never shows a settlement that didn't happen, or misses one that did.
     */
    @Transactional
    public String settle(SettlementRequest request) {
        // Always lock the account first: serializes cash and holdings updates, including duplicate requests.
        var account = accountRepository.findLockedById(request.getAccountId()).orElseThrow(
            () -> new IllegalArgumentException("Account not found"));
        var previous = receipts.findById(request.getOrderId());
        if (previous.isPresent()) {
            if (!previous.get().matches(request)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Settlement payload differs from its original request");
            return previous.get().getRejectionReason();
        }
        String rejection = null;
        if (request.getOrderType() == SettlementRequest.OrderType.BUY) {
            if (account.getCashBalance().compareTo(request.getQuantity().multiply(request.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP)) < 0)
                rejection = "INSUFFICIENT_CASH";
        } else if (holdingsRepository.findByAccountIdAndInstrumentId(request.getAccountId(), request.getInstrumentId())
                .filter(holding -> holding.canSell(request.getQuantity())).isEmpty()) {
            rejection = "INSUFFICIENT_HOLDINGS";
        }
        receipts.save(new com.lemarketjames.holdings.entity.SettlementReceipt(request, rejection));
        if (rejection != null) return rejection;
        BigDecimal cost = request.getQuantity().multiply(request.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP);
        boolean buy = request.getOrderType() == SettlementRequest.OrderType.BUY;
        if (buy) {
            settleBuy(request, cost);
        } else {
            settleSell(request, cost);
        }
        auditRecorder.record(AuditEventType.SETTLED, request.getOrderId(), request.getAccountId(), Map.of(
                "cashDelta", buy ? cost.negate() : cost,
                "quantityDelta", buy ? request.getQuantity() : request.getQuantity().negate()));
        log.info("Settled order={} account={} instrument={} type={} qty={} price={}",
                request.getOrderId(), request.getAccountId(), request.getInstrumentId(),
                request.getOrderType(), request.getQuantity(), request.getPricePerUnit());
        return null;
    }

    private void settleBuy(SettlementRequest request, BigDecimal cost) {
        AccountEntity account = findAccount(request.getAccountId());
        if (account.getCashBalance().compareTo(cost) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient cash to settle order " + request.getOrderId());
        }
        account.setCashBalance(account.getCashBalance().subtract(cost));
        accountRepository.save(account);

        Optional<HoldingsEntity> existing = holdingsRepository
                .findByAccountIdAndInstrumentId(request.getAccountId(), request.getInstrumentId());

        if (existing.isPresent()) {
            HoldingsEntity holding = existing.get();
            BigDecimal newQuantity = holding.getQuantity().add(request.getQuantity());
            BigDecimal newCostBasis = holding.getQuantity().multiply(holding.getAverageCost()).add(cost);
            holding.setAverageCost(newCostBasis.divide(newQuantity, 4, RoundingMode.HALF_UP));
            holding.setQuantity(newQuantity);
            holdingsRepository.save(holding);
        } else {
            holdingsRepository.save(new HoldingsEntity(
                    request.getAccountId(), request.getInstrumentId(), request.getQuantity(), request.getPricePerUnit()));
        }
    }

    private void settleSell(SettlementRequest request, BigDecimal proceeds) {
        HoldingsEntity holding = holdingsRepository
                .findByAccountIdAndInstrumentId(request.getAccountId(), request.getInstrumentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No holding to settle sell order " + request.getOrderId()));

        if (!holding.canSell(request.getQuantity())) {
            throw new IllegalArgumentException(
                    "Insufficient holdings to settle order " + request.getOrderId());
        }

        BigDecimal remaining = holding.getQuantity().subtract(request.getQuantity());
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            holdingsRepository.delete(holding);
        } else {
            // Average cost is unchanged for the shares that remain; only realized gain (not tracked
            // yet) would use the sale price.
            holding.setQuantity(remaining);
            holdingsRepository.save(holding);
        }

        AccountEntity account = findAccount(request.getAccountId());
        account.setCashBalance(account.getCashBalance().add(proceeds));
        accountRepository.save(account);
    }

    private AccountEntity findAccount(Integer accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));
    }
}
