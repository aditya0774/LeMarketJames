package com.lemarketjames.portfolio;

import com.lemarketjames.portfolio.dto.PortfolioResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The GET /api/balance contract API-CONTRACTS.md already documents (unimplemented until now).
 * No accountId param, per that section's own rule: scoped from the JWT like /api/auth/me. Also
 * mapped at /api/v1/portfolio and /api/v1/balance (the /api/v1/ convention alias it calls for).
 */
@RestController
@RequestMapping({"/api/balance", "/api/v1/balance", "/api/v1/portfolio"})
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public PortfolioResponse getPortfolio() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return new PortfolioResponse(portfolioService.getOwnPortfolio(username));
    }
}
