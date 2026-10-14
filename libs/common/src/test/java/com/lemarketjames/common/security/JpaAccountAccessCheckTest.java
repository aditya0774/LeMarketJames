package com.lemarketjames.common.security;

import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.StaffUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;

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
        when(clients.findAccountStatusByUsername("alice")).thenReturn(Optional.of(AccountStatus.ACTIVE));

        assertEquals(Optional.empty(), check.revokedReason("alice", Set.of(Role.CLIENT)));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void aClientThatIsNoLongerActiveIsRevokedWithItsStatus(AccountStatus status) {
        when(clients.findAccountStatusByUsername("alice")).thenReturn(Optional.of(status));

        assertEquals(Optional.of(status.name()), check.revokedReason("alice", Set.of(Role.CLIENT)));
    }

    // "Deleted" means the row is gone: the token outlived its account.
    @Test
    void aDeletedClientIsRevokedAsNotFound() {
        when(clients.findAccountStatusByUsername("ghost")).thenReturn(Optional.empty());

        assertEquals(Optional.of(SecurityEventLog.REASON_NOT_FOUND), check.revokedReason("ghost", Set.of(Role.CLIENT)));
    }

    // One query per request: a client token never falls through to staff_users.
    @Test
    void aClientTokenNeverQueriesStaff() {
        when(clients.findAccountStatusByUsername("ghost")).thenReturn(Optional.empty());

        check.revokedReason("ghost", Set.of(Role.CLIENT));

        verify(staff, never()).findActiveByUsername(anyString());
    }

    @Test
    void anActiveStaffUserIsAllowed() {
        when(staff.findActiveByUsername("olivia")).thenReturn(Optional.of(true));

        assertEquals(Optional.empty(), check.revokedReason("olivia", Set.of(Role.TRADING_OPS)));
    }

    @Test
    void aDeactivatedStaffUserIsRevoked() {
        when(staff.findActiveByUsername("olivia")).thenReturn(Optional.of(false));

        assertEquals(Optional.of(SecurityEventLog.REASON_DEACTIVATED), check.revokedReason("olivia", Set.of(Role.ANALYST)));
    }

    @Test
    void aDeletedStaffUserIsRevokedAsNotFound() {
        when(staff.findActiveByUsername("gone")).thenReturn(Optional.empty());

        assertEquals(Optional.of(SecurityEventLog.REASON_NOT_FOUND), check.revokedReason("gone", Set.of(Role.TRADING_OPS)));
        verify(clients, never()).findAccountStatusByUsername(anyString());
    }
}
