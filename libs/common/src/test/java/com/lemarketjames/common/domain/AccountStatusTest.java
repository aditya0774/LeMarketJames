package com.lemarketjames.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStatusTest {

    @Test
    void anActiveClientMayLogIn() {
        assertTrue(AccountStatus.ACTIVE.canLogIn());
    }

    // LMKT-57: every other status loses access, so a status added later is barred until someone decides otherwise.
    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void everyOtherStatusMayNotLogInOrTrade(AccountStatus status) {
        assertFalse(status.canLogIn());
        assertFalse(status.canTrade());
    }
}
