package com.lemarketjames.orders.service;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
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
    private final ClientRepository clientRepository;

    public AccountAccess(AccountRepository accountRepository, ClientRepository clientRepository) {
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
    }

    /** The authenticated caller's client ID, or null for a caller who isn't a client (staff). */
    public Integer callerClientId() {
        return clientRepository.findByUsername(authenticatedUsername())
            .map(ClientEntity::getClientId)
            .orElse(null);
    }

    /** The authenticated caller's account ID, or null for callers without a trading account. */
    public Integer callerAccountId() {
        return accountRepository.findAccountIdByUsername(authenticatedUsername()).orElse(null);
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
