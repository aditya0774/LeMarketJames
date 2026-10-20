package com.lemarketjames.common.security;

import com.lemarketjames.common.domain.AccountAccessState;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.StaffUserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.time.Instant;

/**
 * Looks the token's account up in the shared database (LMKT-57). The token's roles say which table
 * it lives in, so each request costs exactly one query on a unique, indexed username column.
 * Clients follow {@link AccountStatus#canLogIn()}; staff follow their {@code active} flag.
 */
@Component
public class JpaAccountAccessCheck implements AccountAccessCheck {

    private final ClientRepository clients;
    private final StaffUserRepository staff;

    public JpaAccountAccessCheck(ClientRepository clients, StaffUserRepository staff) {
        this.clients = clients;
        this.staff = staff;
    }

    @Override
    public Optional<String> revokedReason(String username, Set<Role> roles, Instant issuedAt) {
        if (roles.contains(Role.CLIENT)) {
            Optional<AccountAccessState> state = clients.findAccessStateByUsername(username);
            if (state.isEmpty()) {
                return Optional.of(SecurityEventLog.REASON_NOT_FOUND);
            }
            AccountAccessState account = state.get();
            if (!account.accountStatus().canLogIn()) {
                return Optional.of(account.accountStatus().name());
            }
            return invalidated(account.sessionInvalidatedAt(), issuedAt);
        }
        Optional<AccountAccessState> state = staff.findAccessStateByUsername(username);
        if (state.isEmpty()) {
            return Optional.of(SecurityEventLog.REASON_NOT_FOUND);
        }
        AccountAccessState account = state.get();
        if (!Boolean.TRUE.equals(account.active())) {
            return Optional.of(SecurityEventLog.REASON_DEACTIVATED);
        }
        return invalidated(account.sessionInvalidatedAt(), issuedAt);
    }

    private Optional<String> invalidated(Instant invalidatedAt, Instant issuedAt) {
        return invalidatedAt != null && !issuedAt.isAfter(invalidatedAt)
                ? Optional.of(SecurityEventLog.REASON_SESSION_INVALIDATED)
                : Optional.empty();
    }
}
