package com.lemarketjames.auth;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BR-01: a registration is all or nothing. The client, address and account are saved in one
 * transaction, so a failure after the client row is written leaves no half-registered client
 * who could never sign in properly (and whose email would be taken).
 */
@SpringBootTest
class RegistrationRollbackTest {

    @Autowired AuthService auth;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    /** The last of the three saves fails, after the client and address rows are written. */
    @MockitoBean AccountRepository accounts;

    @Test
    void aFailureAfterTheClientIsSavedLeavesNoClient() {
        when(accounts.save(any(AccountEntity.class))).thenThrow(new IllegalStateException("database went away"));
        long addressesBefore = addresses.count();

        assertThrows(IllegalStateException.class, () -> auth.register(RegistrationRulesTest.valid()));

        assertFalse(clients.existsByUsername("jane_doe"));
        assertFalse(clients.existsByEmail("jane@example.com"));
        assertEquals(addressesBefore, addresses.count());
    }
}
