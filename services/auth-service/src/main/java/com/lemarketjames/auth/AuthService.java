package com.lemarketjames.auth;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.domain.LoginAccount;
import com.lemarketjames.common.domain.StaffUserEntity;
import com.lemarketjames.common.domain.StaffUserRepository;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.security.AccountInactiveException;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.SecurityEventLog;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service class providing authentication and authorization business logic.
 * Handles user registration, login, password encoding, and token generation.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ClientRepository clientRepository;
    private final AddressRepository addressRepository;
    private final AccountRepository accountRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PlatformSettings.Auth.Lockout lockout;
    private final Clock clock;
    private final RegistrationRules registrationRules;
    private final SessionTokens sessionTokens;

    /** Resolve the trading account from the server-authenticated identity. */
    public Integer getAccountId(String username) {
        return accountRepository.findAccountIdByUsername(username)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
    }

    /**
     * The details a staff member's session shows about them (LMKT-144): what staff_users already
     * stores, read only. Empty for a client or a staff login that no longer exists.
     */
    public Optional<StaffProfile> getStaffProfile(String username) {
        return staffUserRepository.findByUsername(username)
            .map(staff -> new StaffProfile(staff.getEmail(), staff.getFullName()));
    }

    /** A staff member's own email and full name, for the staff app's Employee information page. */
    public record StaffProfile(String email, String fullName) {
    }

    /**
    * Constructs an AuthService with its authentication and persistence dependencies.
     *
     * @param jwtService the JWT service for token generation and validation
     * @param clientRepository repository for client records
     * @param addressRepository repository for client addresses
     * @param accountRepository repository for trading accounts
     * @param staffUserRepository repository for staff logins
     * @param settings platform settings; supplies the lockout rule and the inactivity timeout (contract C5)
     */
    @Autowired
    public AuthService(
            JwtService jwtService,
            ClientRepository clientRepository,
            AddressRepository addressRepository,
            AccountRepository accountRepository,
            StaffUserRepository staffUserRepository,
            PlatformSettings settings) {
        this(jwtService, clientRepository, addressRepository, accountRepository, staffUserRepository, settings,
                Clock.systemUTC());
    }

    /**
     * Takes the clock the lockout and session tokens are timed with, so tests can move time
     * instead of sleeping. Give the JwtService the same clock, or the tokens it checks will look
     * expired or not yet issued.
     */
    AuthService(
            JwtService jwtService,
            ClientRepository clientRepository,
            AddressRepository addressRepository,
            AccountRepository accountRepository,
            StaffUserRepository staffUserRepository,
            PlatformSettings settings,
            Clock clock) {
        this.clientRepository = clientRepository;
        this.addressRepository = addressRepository;
        this.accountRepository = accountRepository;
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtService = jwtService;
        this.lockout = settings.getAuth().getLockout();
        this.clock = clock;
        // Built here, not injected: the rules hold no state but the clock the age is counted on.
        this.registrationRules = new RegistrationRules(clock);
        this.sessionTokens = new SessionTokens(jwtService, settings.getSession().getInactivityTimeout(),
                Duration.ofSeconds(jwtService.getExpirationSeconds()), clock);
    }

    /**
     * Registers a new user with the provided registration request.
     * Validates the request, checks for duplicate usernames, and stores the user with an encoded password.
     *
     * @param request the registration request containing user details
     * @return an AuthResponse with the registered username and success message
     * @throws ValidationException if a field breaks a registration rule ({@link RegistrationRules})
     * @throws IllegalArgumentException if the username or email is already taken
     */
    // One transaction for the client, address and account: a failure part-way leaves no half-registered client.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername();
        String email = request.getEmail().toLowerCase();

        if (clientRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }

        if (clientRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        String encodedPassword = encodePassword(request.getPassword());
        client.setPassword(encodedPassword);
        client.setEmail(email);
        client.setFullName(request.getFullName());
        client.setDateOfBirth(request.getDateOfBirth());
        client.setPhone(request.getPhoneNumber());
        client.setRegisteredDate(LocalDateTime.now());
        // SSN is hashed like a password: it's never used for lookups or validation.
        client.setSsn(encodePassword(request.getSsn()));
        client.setEmploymentStatus(request.getEmploymentStatus().trim().toUpperCase());
        // PostgreSQL's investment_experience constraint uses lowercase values.
        client.setInvestmentExperience(request.getInvestmentExperience().trim().toLowerCase(Locale.ROOT));
        client.setAccountStatus(AccountStatus.ACTIVE);
        client = clientRepository.save(client);

        AddressEntity address = new AddressEntity();
        address.setClientId(client.getClientId());
        address.setAddressType("RESIDENTIAL");
        address.setStreetAddress(request.getStreetAddress());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getZipCode());
        String country = request.getCountry();
        address.setCountry(country == null || country.isBlank() ? "US" : country);
        addressRepository.save(address);

        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(request.getInitialDeposit());
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        accountRepository.save(account);

        log.info("Registration succeeded for username={}", username);

        return new AuthResponse(username, "User registered successfully");
    }

    /**
     * Authenticates a client or staff user by email and password (contract C7: one login for both).
     * The request's username field carries the email; it is matched case-insensitively
     * because registration stores emails lowercased. The password is verified against
     * the stored BCrypt hash.
     *
     * <p>Lockout (contract C5) is stored on the login itself, so it survives restarts: after
     * {@code lmj.auth.lockout.max-attempts} consecutive wrong passwords the login is locked for
     * {@code lmj.auth.lockout.duration}.
     *
     * @param request the login request containing the email (as username) and password
     * @return a LoginResult with the username, success message, JWT token and roles
     * @throws IllegalArgumentException if validation fails, the login is locked, or credentials are invalid
     * @throws AccountInactiveException if the password is right but the login may no longer be used (LMKT-57)
     */
    public LoginResult login(LoginRequest request) {
        validateLoginRequest(request);

        String email = request.getUsername().trim().toLowerCase(Locale.ROOT);
        String password = request.getPassword();

        // Clients first: they are the vast majority of logins. Staff never share an email with a client.
        LoginAccount account = clientRepository.findByEmail(email)
                .<LoginAccount>map(client -> client)
                .or(() -> staffUserRepository.findByEmail(email))
                .orElse(null);
        if (account == null) {
            log.warn("Login failed for email={}", email);
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (account.getLockedUntil() != null && clock.instant().isBefore(account.getLockedUntil())) {
            log.warn("Login blocked for email={} due to active lockout", email);
                throw new IllegalArgumentException(lockoutMessage("Account temporarily locked. Try again in ",
                    Duration.between(clock.instant(), account.getLockedUntil())));
        }

        if (!matchesPassword(password, account.getPassword())) {
            Instant lockedUntil = registerFailedAttempt(account);
            if (lockedUntil != null) {
                log.warn("ACCOUNT_LOCKED email={} duration={}", email, formatDuration(lockout.getDuration()));
                throw new IllegalArgumentException(lockoutMessage("Account locked for ", lockout.getDuration()));
            }
            log.warn("Login failed for email={}", email);
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Checked only after the password, so a wrong guess never reveals an account's status.
        if (!account.canLogIn()) {
            SecurityEventLog.accessRevoked(account.getUsername(), inactiveReason(account), clock.instant());
            throw new AccountInactiveException();
        }

        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        if (account instanceof ClientEntity client) {
            client.setLastLogin(LocalDateTime.now());
        }
        persist(account);

        // The token subject stays the username: /me, orders and account ownership checks look users up by it.
        String username = account.getUsername();
        Set<Role> roles = account.roles();
        SessionTokens.SessionToken token = sessionTokens.signIn(username, roles, loginIssuedAt(account));
        log.info("Login succeeded for username={} roles={}", username, roles);
        return new LoginResult(username, "Login successful", token.value(), roles, token.expiresAt());
    }

    /**
     * Extends a session the user is still using (BR-03): a new token for another inactivity
     * timeout, never past the session cap. The web apps call it on user activity only, so a
     * session left open with nobody at it still ends.
     *
     * @param token the current session token
     * @return the new token and its expiry, or empty if the token is expired, invalid or past the cap
     */
    public Optional<SessionTokens.SessionToken> refresh(String token) {
        return sessionTokens.refresh(token);
    }

    /** When the given session token stops being accepted; empty if it is not valid now. */
    public Optional<Instant> sessionExpiry(String token) {
        return sessionTokens.expiryOf(token);
    }

    /** How long a session may sit idle before it ends (contract C5). */
    public Duration inactivityTimeout() {
        return sessionTokens.inactivityTimeout();
    }

    /** The time sessions are measured on, so a reply's "expires in" matches the token. */
    public Instant now() {
        return clock.instant();
    }

        private Instant loginIssuedAt(LoginAccount account) {
        Instant now = clock.instant();
        Instant invalidatedAt = account instanceof ClientEntity client
            ? client.getSessionInvalidatedAt()
            : ((StaffUserEntity) account).getSessionInvalidatedAt();
        // JWT timestamps have second precision, so compare at that precision too. A login a few
        // milliseconds after logout can otherwise receive the same iat and be rejected again.
        return invalidatedAt != null && now.getEpochSecond() <= invalidatedAt.getEpochSecond()
            ? invalidatedAt.plusSeconds(1)
            : null;
        }

    /** Invalidates all tokens issued for the account at or before the logout time. */
    @Transactional
    public void logout(String token) {
        jwtService.extractClaims(token).ifPresent(claims -> {
            if (claims.roles().contains(Role.CLIENT)) {
                clientRepository.findByUsername(claims.username()).ifPresent(client -> {
                    client.setSessionInvalidatedAt(latestInvalidation(client.getSessionInvalidatedAt()));
                    clientRepository.save(client);
                });
            } else {
                staffUserRepository.findByUsername(claims.username()).ifPresent(staff -> {
                    staff.setSessionInvalidatedAt(latestInvalidation(staff.getSessionInvalidatedAt()));
                    staffUserRepository.save(staff);
                });
            }
        });
    }

    private Instant latestInvalidation(Instant previousInvalidation) {
        Instant now = clock.instant();
        return previousInvalidation == null || previousInvalidation.isBefore(now) ? now : previousInvalidation;
    }

    // Counts a wrong password; reaching the limit locks the login and starts a fresh count for afterwards.
    // The lock is timed from now, after the (slow) password check, so it lasts the full duration.
    private Instant registerFailedAttempt(LoginAccount account) {
        int attempts = account.getFailedLoginAttempts() + 1;
        Instant lockedUntil = null;
        if (attempts >= lockout.getMaxAttempts()) {
            lockedUntil = clock.instant().plus(lockout.getDuration());
            account.setLockedUntil(lockedUntil);
            attempts = 0;
        }
        account.setFailedLoginAttempts(attempts);
        persist(account);
        return lockedUntil;
    }

    // A client's reason is its status (CLOSED, SUSPENDED, EXPIRED); staff only have an active flag.
    private String inactiveReason(LoginAccount account) {
        if (account instanceof ClientEntity client && client.getAccountStatus() != null) {
            return client.getAccountStatus().name();
        }
        return SecurityEventLog.REASON_DEACTIVATED;
    }

    private String lockoutMessage(String prefix, Duration duration) {
        return prefix + formatDuration(duration) + ".";
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(1, duration.getSeconds());
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    private void persist(LoginAccount account) {
        if (account instanceof ClientEntity client) {
            clientRepository.save(client);
        } else {
            staffUserRepository.save((StaffUserEntity) account);
        }
    }

    /**
     * Encodes a raw password using BCrypt with a salt.
     *
     * @param rawPassword the plain text password to encode
     * @return the encoded password hash
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verifies that a raw password matches the encoded password hash.
     *
     * @param rawPassword the plain text password to verify
     * @param encodedPassword the encoded password hash to compare against
     * @return true if the passwords match, false otherwise
     */
    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * Applies the registration rules (BR-01). The server is authoritative; the trading app's form
     * mirrors the same rules so mistakes are caught before sending.
     *
     * @param request the registration request to validate
     * @throws ValidationException with every broken field and its message
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        Map<String, String> errors = registrationRules.check(request);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validates a login request ensuring all required fields are present and non-empty.
     *
     * @param request the login request to validate
     * @throws IllegalArgumentException if any required field is missing or invalid
     */
    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        validateRequired(request.getUsername(), "Email is required");
        validateRequired(request.getPassword(), "Password is required");
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Response object for authentication operations containing username and message.
     */
    public static class AuthResponse {
        private final String username;
        private final String message;

        public AuthResponse(String username, String message) {
            this.username = username;
            this.message = message;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Result object for successful login containing username, message, and JWT token.
     * The token is not serialized directly in the response; it is only used by the controller
     * to set the authentication cookie.
     */
    public static class LoginResult {
        private final String username;
        private final String message;
        private final String token;
        private final Set<Role> roles;
        private final Instant expiresAt;

        public LoginResult(String username, String message, String token, Set<Role> roles, Instant expiresAt) {
            this.username = username;
            this.message = message;
            this.token = token;
            this.roles = roles;
            this.expiresAt = expiresAt;
        }

        /** When the token stops being accepted unless it is refreshed (BR-03); the cookie lasts as long. */
        public Instant getExpiresAt() {
            return expiresAt;
        }

        /** The roles written into the token; tells the controller whether this login has an account. */
        public Set<Role> getRoles() {
            return roles;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }

        public String getToken() {
            return token;
        }
    }
}

