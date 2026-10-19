package com.lemarketjames.cash;

import com.lemarketjames.portfolio.PortfolioService;
import com.lemarketjames.portfolio.dto.PortfolioResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Deposit and withdrawal endpoints from contract C6, scoped entirely from the JWT. */
@RestController
@RequestMapping("/api/v1/portfolio")
public class CashMovementController {

    private final CashMovementService movements;
    private final PortfolioService portfolio;

    public CashMovementController(CashMovementService movements, PortfolioService portfolio) {
        this.movements = movements;
        this.portfolio = portfolio;
    }

    @PostMapping("/deposits")
    public PortfolioResponse deposit(@RequestBody CashMovementRequest request) {
        return move(CashMovementType.DEPOSIT, request);
    }

    @PostMapping("/withdrawals")
    public PortfolioResponse withdraw(@RequestBody CashMovementRequest request) {
        return move(CashMovementType.WITHDRAWAL, request);
    }

    private PortfolioResponse move(CashMovementType type, CashMovementRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        movements.move(username, type, request.amount(), requestId(request.requestId()));
        return new PortfolioResponse(portfolio.getOwnPortfolio(username));
    }

    private static UUID requestId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new CashMovementException(CashMovementService.INVALID_AMOUNT,
                    "Request ID must be a UUID");
        }
    }
}
