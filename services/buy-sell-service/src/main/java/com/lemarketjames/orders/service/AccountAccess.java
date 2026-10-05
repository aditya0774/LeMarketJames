package com.lemarketjames.orders.service;

import com.lemarketjames.common.domain.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is calling, and whether an account is theirs. Shared by order reads and the submission
 * checks so the ownership rule and its denial are defined once.
 */
@Component
public class AccountAccess {

    private static final Logger log = LoggerFactory.getLogger(AccountAccess.class);

    private final AccountRepository accountRepository;

    public AccountAccess(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required");
        }
        return authentication.getName();
    }

    public void requireOwnAccount(Integer accountId) {
        String username = authenticatedUsername();
        boolean ownsAccount = accountRepository.existsByAccountIdAndUsername(accountId, username);
        if (!ownsAccount) {
            log.warn("Order access denied for username={} accountId={}", username, accountId);
            throw new AccessDeniedException("Account access is not allowed");
        }
    }
}
