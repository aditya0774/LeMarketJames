package com.lemarketjames.common.security;

import com.lemarketjames.common.domain.AccountAccessState;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.StaffUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LMKT-57: which accounts keep their session, and that each check is a single lookup. */
class JpaAccountAccessCheckTest {

    private final ClientRepository clients = mock(ClientRepository.class);
    private final StaffUserRepository staff = mock(StaffUserRepository.class);
    private final JpaAccountAccessCheck check = new JpaAccountAccessCheck(clients, staff);

    @Test
    void anActiveClientIsAllowed() {
        when(clients.findAccessStateByUsername("alice"))
            .thenReturn(Optional.of(new AccountAccessState(AccountStatus.ACTIVE, null, null)));

        assertEquals(Optional.empty(), check.revokedReason("alice", Set.of(Role.CLIENT)));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void aClientThatIsNoLongerActiveIsRevokedWithItsStatus(AccountStatus status) {
        when(clients.findAccessStateByUsername("alice"))
            .thenReturn(Optional.of(new AccountAccessState(status, null, null)));

        assertEquals(Optional.of(status.name()), check.revokedReason("alice", Set.of(Role.CLIENT)));
    }

    // "Deleted" means the row is gone: the token outlived its account.
    @Test
    void aDeletedClientIsRevokedAsNotFound() {
        when(clients.findAccessStateByUsername("ghost")).thenReturn(Optional.empty());

        assertEquals(Optional.of(SecurityEventLog.REASON_NOT_FOUND), check.revokedReason("ghost", Set.of(Role.CLIENT)));
    }

    // One query per request: a client token never falls through to staff_users.
    @Test
    void aClientTokenNeverQueriesStaff() {
        when(clients.findAccessStateByUsername("ghost")).thenReturn(Optional.empty());

        check.revokedReason("ghost", Set.of(Role.CLIENT));

        verify(staff, never()).findAccessStateByUsername(anyString());
    }

        @Test
        void aClientTokenIssuedAtOrBeforeTheCutoffIsRevoked() {
        Instant invalidatedAt = Instant.parse("2026-10-16T19:00:00Z");
        when(clients.findAccessStateByUsername("alice"))
            .thenReturn(Optional.of(new AccountAccessState(AccountStatus.ACTIVE, null, invalidatedAt)));

        assertEquals(Optional.of(SecurityEventLog.REASON_SESSION_INVALIDATED),
            check.revokedReason("alice", Set.of(Role.CLIENT), invalidatedAt.minusSeconds(1)));
        assertEquals(Optional.of(SecurityEventLog.REASON_SESSION_INVALIDATED),
            check.revokedReason("alice", Set.of(Role.CLIENT), invalidatedAt));
        }

        @Test
        void aClientTokenIssuedAfterTheCutoffIsAllowed() {
        Instant invalidatedAt = Instant.parse("2026-10-16T19:00:00Z");
        when(clients.findAccessStateByUsername("alice"))
            .thenReturn(Optional.of(new AccountAccessState(AccountStatus.ACTIVE, null, invalidatedAt)));

        assertEquals(Optional.empty(),
            check.revokedReason("alice", Set.of(Role.CLIENT), invalidatedAt.plusSeconds(1)));
        }

    @Test
    void anActiveStaffUserIsAllowed() {
        when(staff.findAccessStateByUsername("olivia"))
            .thenReturn(Optional.of(new AccountAccessState(null, true, null)));

        assertEquals(Optional.empty(), check.revokedReason("olivia", Set.of(Role.TRADING_OPS)));
    }

    @Test
    void aDeactivatedStaffUserIsRevoked() {
        when(staff.findAccessStateByUsername("olivia"))
            .thenReturn(Optional.of(new AccountAccessState(null, false, null)));

        assertEquals(Optional.of(SecurityEventLog.REASON_DEACTIVATED), check.revokedReason("olivia", Set.of(Role.ANALYST)));
    }

    @Test
    void aStaffTokenIssuedBeforeTheCutoffIsRevoked() {
        Instant invalidatedAt = Instant.parse("2026-10-16T19:00:00Z");
        when(staff.findAccessStateByUsername("olivia"))
                .thenReturn(Optional.of(new AccountAccessState(null, true, invalidatedAt)));

        assertEquals(Optional.of(SecurityEventLog.REASON_SESSION_INVALIDATED),
                check.revokedReason("olivia", Set.of(Role.ANALYST), invalidatedAt.minusSeconds(1)));
    }

    @Test
    void aDeletedStaffUserIsRevokedAsNotFound() {
        when(staff.findAccessStateByUsername("gone")).thenReturn(Optional.empty());

        assertEquals(Optional.of(SecurityEventLog.REASON_NOT_FOUND), check.revokedReason("gone", Set.of(Role.TRADING_OPS)));
        verify(clients, never()).findAccessStateByUsername(anyString());
    }
}
