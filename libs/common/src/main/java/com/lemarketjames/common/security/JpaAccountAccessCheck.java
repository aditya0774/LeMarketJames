package com.lemarketjames.common.security;

import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.StaffUserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

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
    public Optional<String> revokedReason(String username, Set<Role> roles) {
        if (roles.contains(Role.CLIENT)) {
            Optional<AccountStatus> status = clients.findAccountStatusByUsername(username);
            if (status.isEmpty()) {
                return Optional.of(SecurityEventLog.REASON_NOT_FOUND);
            }
            return status.get().canLogIn() ? Optional.empty() : Optional.of(status.get().name());
        }
        Optional<Boolean> active = staff.findActiveByUsername(username);
        if (active.isEmpty()) {
            return Optional.of(SecurityEventLog.REASON_NOT_FOUND);
        }
        return Boolean.TRUE.equals(active.get()) ? Optional.empty() : Optional.of(SecurityEventLog.REASON_DEACTIVATED);
    }
}
