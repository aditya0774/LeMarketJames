package com.lemarketjames.profile;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.profile.dto.ProfileDto;
import org.springframework.stereotype.Service;

/**
 * Self-service only: a client can fetch their own profile, resolved from the JWT's username, same
 * as every other own-data endpoint in this app (holdings, orders). There is no lookup-by-id — that
 * would need an authorization model (e.g. an admin role) that doesn't exist anywhere in this app.
 */
@Service
public class ProfileService {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;

    public ProfileService(ClientRepository clientRepository, AccountRepository accountRepository) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
    }

    public ProfileDto getOwnProfile(String username) {
        ClientEntity client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No client found for username: " + username));

        Integer accountId = accountRepository.findAccountIdByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No account found for username: " + username));
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));

        return new ProfileDto(
                client.getUsername(),
                client.getFullName(),
                client.getEmail(),
                client.getPhone(),
                accountId,
                account.getCashBalance(),
                account.getCurrency(),
                account.isTradingEnabled(),
                account.getOpenedDate());
    }
}
