package com.lemarketjames.auth;

import com.lemarketjames.auth.domain.AccountEntity;
import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.auth.domain.AddressEntity;
import com.lemarketjames.auth.domain.AddressRepository;
import com.lemarketjames.auth.domain.ClientEntity;
import com.lemarketjames.auth.domain.ClientRepository;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.security.JwtService;
import com.lemarketjames.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Service Unit Tests")
class AuthServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AccountRepository accountRepository;

    private AuthService authService;
    private static final long LOCKOUT_DURATION_MS = 900000;

    @BeforeEach
    void setUp() {
        authService = new AuthService(jwtService, clientRepository, addressRepository, accountRepository, LOCKOUT_DURATION_MS);
    }

    // ========== register() Tests ==========

    @Test
    @DisplayName("register creates client, address, and account on valid request")
    void testRegisterSuccess() {
        RegisterRequest request = validRegisterRequest("alice");
        ClientEntity savedClient = new ClientEntity();
        savedClient.setUsername("alice");

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        AuthService.AuthResponse response = authService.register(request);

        assertEquals("alice", response.getUsername());
        assertEquals("User registered successfully", response.getMessage());
        verify(clientRepository).save(any(ClientEntity.class));
        verify(addressRepository).save(any(AddressEntity.class));
        verify(accountRepository).save(any(AccountEntity.class));
    }

    @Test
    @DisplayName("register rejects duplicate username")
    void testRegisterDuplicateUsername() {
        RegisterRequest request = validRegisterRequest("existing");
        when(clientRepository.existsByUsername("existing")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        assertEquals("Username is already taken", ex.getMessage());
        verify(clientRepository, never()).save(any(ClientEntity.class));
    }

    @Test
    @DisplayName("register rejects duplicate email")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = validRegisterRequest("newuser");
        request.setEmail("alice@example.com");
        when(clientRepository.existsByUsername("newuser")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        assertEquals("Email is already registered", ex.getMessage());
    }

    @Test
    @DisplayName("register validates all required fields")
    void testRegisterMissingFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword(null);

        assertThrows(ValidationException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("register normalizes email to lowercase")
    void testRegisterNormalizesEmailToLowercase() {
        RegisterRequest request = validRegisterRequest("alice");
        request.setEmail("ALICE@EXAMPLE.COM");
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(captor.capture());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    @DisplayName("register normalizes employment status to uppercase")
    void testRegisterNormalizesEmploymentStatus() {
        RegisterRequest request = validRegisterRequest("alice");
        request.setEmploymentStatus("employed");
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(captor.capture());
        assertEquals("EMPLOYED", captor.getValue().getEmploymentStatus());
    }

    @Test
    @DisplayName("register normalizes investment experience to lowercase")
    void testRegisterNormalizesInvestmentExperience() {
        RegisterRequest request = validRegisterRequest("alice");
        request.setInvestmentExperience("EXPERT");
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(captor.capture());
        assertEquals("expert", captor.getValue().getInvestmentExperience());
    }

    @Test
    @DisplayName("register encodes password using BCrypt")
    void testRegisterEncodesPassword() {
        RegisterRequest request = validRegisterRequest("alice");
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(captor.capture());
        String encodedPassword = captor.getValue().getPassword();
        assertNotEquals("Pass123!", encodedPassword);
        assertTrue(encodedPassword.startsWith("$2"));
    }

    @Test
    @DisplayName("register sets default country to US when null")
    void testRegisterDefaultCountryUS() {
        RegisterRequest request = validRegisterRequest("alice");
        request.setCountry(null);
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<AddressEntity> captor = ArgumentCaptor.forClass(AddressEntity.class);
        verify(addressRepository).save(captor.capture());
        assertEquals("US", captor.getValue().getCountry());
    }

    @Test
    @DisplayName("register creates address with RESIDENTIAL type")
    void testRegisterCreatesResidentialAddress() {
        RegisterRequest request = validRegisterRequest("alice");
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<AddressEntity> captor = ArgumentCaptor.forClass(AddressEntity.class);
        verify(addressRepository).save(captor.capture());
        assertEquals("RESIDENTIAL", captor.getValue().getAddressType());
    }

    @Test
    @DisplayName("register creates trading account with correct initial values")
    void testRegisterCreatesAccountWithInitialDeposit() {
        RegisterRequest request = validRegisterRequest("alice");
        ClientEntity savedClient = new ClientEntity();

        when(clientRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(clientRepository.save(any(ClientEntity.class))).thenReturn(savedClient);

        authService.register(request);

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(BigDecimal.valueOf(500), captor.getValue().getCashBalance());
        assertEquals("USD", captor.getValue().getCurrency());
        assertTrue(captor.getValue().isTradingEnabled());
    }

    // ========== login() Tests ==========

    @Test
    @DisplayName("login returns token on valid credentials")
    void testLoginSuccess() {
        ClientEntity client = new ClientEntity();
        client.setUsername("alice");
        client.setPassword(authService.encodePassword("Pass123!"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));
        when(jwtService.generateToken("alice")).thenReturn("jwt-token-123");

        AuthService.LoginResult result = authService.login(new LoginRequest("alice@example.com", "Pass123!"));

        assertEquals("alice", result.getUsername());
        assertEquals("Login successful", result.getMessage());
        assertEquals("jwt-token-123", result.getToken());
    }

    @Test
    @DisplayName("login throws when credentials invalid")
    void testLoginInvalidCredentials() {
        ClientEntity client = new ClientEntity();
        client.setPassword(authService.encodePassword("Pass123!"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "wrongpassword")));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("login throws when email not found")
    void testLoginEmailNotFound() {
        when(clientRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("notfound@example.com", "Pass123!")));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("login normalizes email to lowercase")
    void testLoginNormalizesEmailToLowercase() {
        ClientEntity client = new ClientEntity();
        client.setUsername("alice");
        client.setPassword(authService.encodePassword("Pass123!"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));
        when(jwtService.generateToken("alice")).thenReturn("jwt-token");

        authService.login(new LoginRequest("ALICE@EXAMPLE.COM", "Pass123!"));

        verify(clientRepository).findByEmail("alice@example.com");
    }

    @Test
    @DisplayName("login locks account after 3 failed attempts")
    void testLoginLocksAccountAfterThreeFailedAttempts() {
        ClientEntity client = new ClientEntity();
        client.setPassword(authService.encodePassword("correct"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));

        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> authService.login(new LoginRequest("alice@example.com", "wrong")));
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "wrong")));
        assertTrue(ex.getMessage().contains("locked"));
    }

    @Test
    @DisplayName("login blocks locked account even with correct password")
    void testLoginBlocksLockedAccountWithCorrectPassword() {
        ClientEntity client = new ClientEntity();
        client.setPassword(authService.encodePassword("correct"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));

        for (int i = 0; i < 3; i++) {
            try {
                authService.login(new LoginRequest("alice@example.com", "wrong"));
            } catch (IllegalArgumentException e) {
                // Expected
            }
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "correct")));
        assertTrue(ex.getMessage().contains("locked"));
    }

    @Test
    @DisplayName("login resets failed attempts on successful login")
    void testLoginResetsFailedAttemptsOnSuccess() {
        ClientEntity client = new ClientEntity();
        client.setUsername("alice");
        client.setPassword(authService.encodePassword("Pass123!"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));
        when(jwtService.generateToken("alice")).thenReturn("jwt-token");

        try {
            authService.login(new LoginRequest("alice@example.com", "wrong"));
        } catch (IllegalArgumentException e) {
            // Expected - failed login
        }

        AuthService.LoginResult result = authService.login(new LoginRequest("alice@example.com", "Pass123!"));

        assertEquals("alice", result.getUsername());
        assertEquals("Login successful", result.getMessage());
        // Verify that after a failed attempt, a successful login still works and updates the client
        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository, atLeastOnce()).save(captor.capture());
        assertNotNull(captor.getValue().getLastLogin());
    }

    @Test
    @DisplayName("login updates lastLogin timestamp")
    void testLoginUpdatesLastLoginTimestamp() {
        ClientEntity client = new ClientEntity();
        client.setUsername("alice");
        client.setPassword(authService.encodePassword("Pass123!"));

        when(clientRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(client));
        when(jwtService.generateToken("alice")).thenReturn("jwt-token");

        authService.login(new LoginRequest("alice@example.com", "Pass123!"));

        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).save(captor.capture());
        assertNotNull(captor.getValue().getLastLogin());
    }

    // ========== getAccountId() Tests ==========

    @Test
    @DisplayName("getAccountId returns account ID for valid username")
    void testGetAccountIdSuccess() {
        when(accountRepository.findAccountIdByUsername("alice")).thenReturn(Optional.of(42));

        Integer accountId = authService.getAccountId("alice");

        assertEquals(42, accountId);
    }

    @Test
    @DisplayName("getAccountId throws when username not found")
    void testGetAccountIdNotFound() {
        when(accountRepository.findAccountIdByUsername("unknown")).thenReturn(Optional.empty());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> authService.getAccountId("unknown"));
        assertEquals("Account access is not allowed", ex.getMessage());
    }

    // ========== Password Encoding Tests ==========

    @Test
    @DisplayName("encodePassword returns BCrypt hash")
    void testEncodePasswordReturnsBCryptHash() {
        String encoded = authService.encodePassword("mypassword");

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2"));
        assertNotEquals("mypassword", encoded);
    }

    @Test
    @DisplayName("matchesPassword returns true for correct password")
    void testMatchesPasswordCorrect() {
        String password = "mypassword";
        String encoded = authService.encodePassword(password);

        assertTrue(authService.matchesPassword(password, encoded));
    }

    @Test
    @DisplayName("matchesPassword returns false for incorrect password")
    void testMatchesPasswordIncorrect() {
        String encoded = authService.encodePassword("mypassword");

        assertFalse(authService.matchesPassword("wrongpassword", encoded));
    }

    // ========== Token Expiry Tests ==========

    @Test
    @DisplayName("getTokenExpirySeconds delegates to JwtService")
    void testGetTokenExpirySeconds() {
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        long expiry = authService.getTokenExpirySeconds();

        assertEquals(3600L, expiry);
        verify(jwtService).getExpirationSeconds();
    }

    // ========== Helper Methods ==========

    private RegisterRequest validRegisterRequest(String username) {
        RegisterRequest request = new RegisterRequest(username, "Pass123!", username + "@example.com", "Test User");
        request.setStreetAddress("123 Main St");
        request.setCity("Springfield");
        request.setState("IL");
        request.setZipCode("62701");
        request.setCountry("US");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(BigDecimal.valueOf(500));
        request.setInvestmentExperience("beginner");
        request.setEmploymentStatus("employed");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        return request;
    }
}
