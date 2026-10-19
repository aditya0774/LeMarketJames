package com.lemarketjames.cash;

import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.audit.CashAuditEvent;
import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Applies one idempotent deposit or withdrawal to the authenticated client's account. */
@Service
public class CashMovementService {

    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    private final AccountRepository accounts;
    private final CashMovementReceiptRepository receipts;
    private final OrdersClient orders;
    private final AuditRecorder audit;
    private final PlatformSettings settings;
    private final Clock clock;

    @Autowired
    public CashMovementService(AccountRepository accounts, CashMovementReceiptRepository receipts,
                               OrdersClient orders, AuditRecorder audit, PlatformSettings settings) {
        this(accounts, receipts, orders, audit, settings, Clock.systemUTC());
    }

    CashMovementService(AccountRepository accounts, CashMovementReceiptRepository receipts,
                        OrdersClient orders, AuditRecorder audit, PlatformSettings settings, Clock clock) {
        this.accounts = accounts;
        this.receipts = receipts;
        this.orders = orders;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public CashMovementResult move(String username, CashMovementType type, BigDecimal amount, UUID requestId) {
        Integer accountId = accounts.findAccountIdByUsername(username)
                .orElseThrow(() -> new CashMovementException(ACCOUNT_NOT_FOUND, "Account not found"));
        AccountEntity account = accounts.findLockedById(accountId)
                .orElseThrow(() -> new CashMovementException(ACCOUNT_NOT_FOUND, "Account not found"));

        CashMovementReceipt existing = receipts.findById(requestId).orElse(null);
        if (existing != null) {
            if (!accountId.equals(existing.getAccountId())) {
                throw new CashMovementException(INVALID_AMOUNT, "Request ID has already been used");
            }
            return new CashMovementResult(existing.getCashBalanceAfter(), true);
        }

        BigDecimal normalized = validate(amount);
        BigDecimal balanceAfter = type == CashMovementType.DEPOSIT
                ? account.getCashBalance().add(normalized)
                : withdraw(account, normalized);
        balanceAfter = balanceAfter.setScale(2, RoundingMode.UNNECESSARY);
        account.setCashBalance(balanceAfter);

        receipts.save(new CashMovementReceipt(
                requestId, accountId, type, normalized, balanceAfter, clock.instant()));
        audit.recordCashMovement(new CashAuditEvent(
                requestId.toString(), accountId, account.getClientId(), type.auditType(),
                Map.of("amount", normalized, "balanceAfter", balanceAfter, "requestId", requestId.toString())));
        return new CashMovementResult(balanceAfter, false);
    }

    private BigDecimal validate(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(settings.getCash().getMovementLimit()) > 0) {
            throw new CashMovementException(INVALID_AMOUNT,
                    "Enter an amount above $0 with no more than two decimal places, up to $"
                            + settings.getCash().getMovementLimit().setScale(2, RoundingMode.HALF_UP) + ".");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal withdraw(AccountEntity account, BigDecimal amount) {
        BigDecimal reserved = orders.getOrdersForAccountRequired(account.getAccountId()).stream()
                .filter(OrderSummary::isOpen)
                .filter(OrderSummary::isBuy)
                .map(order -> order.quantity().multiply(
                        order.pricePerUnit() == null ? BigDecimal.ZERO : order.pricePerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal buyingPower = account.getCashBalance().subtract(reserved);
        if (amount.compareTo(buyingPower) > 0) {
            throw new CashMovementException(INSUFFICIENT_FUNDS,
                    "You can withdraw up to $" + buyingPower.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP) + ".");
        }
        return account.getCashBalance().subtract(amount);
    }
}
