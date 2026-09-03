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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ClientRepository clientRepository;
    private final AddressRepository addressRepository;
    private final AccountRepository accountRepository;

    public AuthService(JwtService jwtService,
                        ClientRepository clientRepository,
                        AddressRepository addressRepository,
                        AccountRepository accountRepository) {
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtService = jwtService;
        this.clientRepository = clientRepository;
        this.addressRepository = addressRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername();
        String email = request.getEmail();

        if (clientRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (clientRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword(encodePassword(request.getPassword()));
        client.setEmail(email);
        client.setFullName(request.getFullName());
        client.setDateOfBirth(request.getDateOfBirth());
        client.setPhone(request.getPhoneNumber());
        client.setRegisteredDate(LocalDateTime.now());
        // SSN is hashed like a password: it's never used for lookups or validation.
        client.setSsn(encodePassword(request.getSsn()));
        client.setEmploymentStatus(request.getEmploymentStatus().trim().toUpperCase());
        client.setInvestmentExperience(request.getInvestmentExperience().trim().toUpperCase());
        client.setAccountStatus("ACTIVE");
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

        return new AuthResponse(username, "User registered successfully");
    }

    public LoginResult login(LoginRequest request) {
        validateLoginRequest(request);

        String username = request.getUsername();
        String password = request.getPassword();

        ClientEntity client = clientRepository.findByUsername(username).orElse(null);
        if (client == null || !matchesPassword(password, client.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtService.generateToken(username);
        return new LoginResult(username, "Login successful", token);
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public long getTokenExpirySeconds() {
        return jwtService.getExpirationSeconds();
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        requireField(errors, "username", request.getUsername(), "Username is required");
        requireField(errors, "password", request.getPassword(), "Password is required");
        requireField(errors, "email", request.getEmail(), "Email is required");
        requireField(errors, "fullName", request.getFullName(), "Full name is required");
        requireField(errors, "streetAddress", request.getStreetAddress(), "Street address is required");
        requireField(errors, "city", request.getCity(), "City is required");
        requireField(errors, "state", request.getState(), "State is required");
        requireField(errors, "zipCode", request.getZipCode(), "ZIP code is required");
        requireField(errors, "ssn", request.getSsn(), "SSN is required");
        requireField(errors, "phoneNumber", request.getPhoneNumber(), "Phone number is required");
        requireField(errors, "investmentExperience", request.getInvestmentExperience(), "Investment experience is required");
        requireField(errors, "employmentStatus", request.getEmploymentStatus(), "Employment status is required");
        if (request.getInitialDeposit() == null) {
            errors.put("initialDeposit", "Initial deposit amount is required");
        }
        if (request.getDateOfBirth() == null) {
            errors.put("dateOfBirth", "Date of birth is required");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getPassword(), "Password is required");
    }

    private void requireField(Map<String, String> errors, String field, String value, String message) {
        if (value == null || value.isBlank()) {
            errors.put(field, message);
        }
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

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

    /** Not serialized directly; the token is only used by the controller to set the auth cookie. */
    public static class LoginResult {
        private final String username;
        private final String message;
        private final String token;

        public LoginResult(String username, String message, String token) {
            this.username = username;
            this.message = message;
            this.token = token;
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

