package com.lemarketjames.common.security;

import com.lemarketjames.auth.domain.AccountEntity;
import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.auth.domain.ClientEntity;
import com.lemarketjames.auth.domain.ClientRepository;
import com.lemarketjames.common.UnauthorizedAccessException;
import org.springframework.stereotype.Service;

/**
 * Service to validate that a user owns a specific account.
 * 
 * This is the "bank manager" that checks records.
 * Example: Does joanna_trader own account 1? YES → proceed. NO → throw exception.
 */
@Service
public class AccountOwnershipValidator {
    
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    
    /**
     * Constructor that injects the repositories.
     * 
     * @param clientRepository access to user records
     * @param accountRepository access to account records
     */
    public AccountOwnershipValidator(ClientRepository clientRepository, AccountRepository accountRepository) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
    }
    
    /**
     * Validates that the given user owns the given account.
     * 
     * Process:
     * 1. Find the client by username
     * 2. Get the client's ID
     * 3. Find the account that belongs to that client
     * 4. Check if the account ID matches the requested account ID
     * 5. If YES → do nothing (validation passed)
     * 6. If NO → throw UnauthorizedAccessException
     * 
     * @param username the username of the authenticated user (e.g., "joanna_trader")
     * @param requestedAccountId the account ID the user is trying to access (e.g., 2)
     * @throws UnauthorizedAccessException if the user doesn't own the account
     */
    public void validateAccountOwnership(String username, Integer requestedAccountId) {
        // Step 1: Find the client in the database by username
        ClientEntity client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "User not found: " + username
                ));
        
        // Step 2: Get the client's ID (this is the owner's ID)
        Integer ownedClientId = client.getClientId();
        
        // Step 3: Find the account that belongs to this client
        // In our schema, each client has exactly ONE account (1:1 relationship)
        AccountEntity ownedAccount = accountRepository.findById(requestedAccountId)
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "Account not found: " + requestedAccountId
                ));
        
        // Step 4: Check if the account belongs to this client
        if (!ownedAccount.getClientId().equals(ownedClientId)) {
            // This user tried to access an account they don't own!
            // Log this attempt (optional, can be added later)
            throw new UnauthorizedAccessException(
                    "User " + username + " attempted to access account " + requestedAccountId + 
                    " which they do not own"
            );
        }
        
        // If we reach here, validation passed! User owns this account.
    }
    
    /**
     * Gets the account ID that belongs to a given user.
     * 
     * This is useful if we want to automatically scope a request to a user's account.
     * 
     * @param username the username of the user
     * @return the account ID that belongs to this user
     * @throws UnauthorizedAccessException if the user is not found
     */
    public Integer getAccountIdForUser(String username) {
        // Find the client by username
        ClientEntity client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "User not found: " + username
                ));
        
        // Get the client's ID
        Integer clientId = client.getClientId();
        
        // Find the account by client ID
        // Note: In the current schema, each client has exactly one account
        // So we search for an account where client_id matches
        return accountRepository.findAll().stream()
                .filter(account -> account.getClientId().equals(clientId))
                .map(AccountEntity::getAccountId)
                .findFirst()
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "No account found for user: " + username
                ));
    }
}
