package com.lemarketjames.profile;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.profile.dto.ProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AccountRepository accountRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(clientRepository, accountRepository);
    }

    @Test
    void returnsOwnProfileAssembledFromClientAndAccount() {
        ClientEntity client = new ClientEntity();
        client.setUsername("alice");
        client.setFullName("Alice Smith");
        client.setEmail("alice@example.com");
        client.setPhone("555-1234");

        AccountEntity account = new AccountEntity();
        account.setCashBalance(new BigDecimal("1000.00"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.of(2024, 1, 1));

        when(clientRepository.findByUsername("alice")).thenReturn(Optional.of(client));
        when(accountRepository.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
        when(accountRepository.findById(7)).thenReturn(Optional.of(account));

        ProfileDto profile = profileService.getOwnProfile("alice");

        assertEquals("alice", profile.getUsername());
        assertEquals("Alice Smith", profile.getFullName());
        assertEquals("alice@example.com", profile.getEmail());
        assertEquals(new BigDecimal("1000.00"), profile.getCashBalance());
        assertEquals("USD", profile.getCurrency());
        assertEquals(7, profile.getAccountId());
    }

    @Test
    void throwsWhenNoClientForUsername() {
        when(clientRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> profileService.getOwnProfile("ghost"));
    }
}
