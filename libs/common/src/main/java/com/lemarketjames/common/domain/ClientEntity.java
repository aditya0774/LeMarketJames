package com.lemarketjames.common.domain;

import com.lemarketjames.common.security.Role;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** Maps to the `clients` table (database/schema/001_core_schema.sql): a registered user's identity and profile. */
@Entity
@Table(name = "clients")
public class ClientEntity implements LoginAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Integer clientId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String phone;

    @Column(name = "registered_date", nullable = false)
    private LocalDateTime registeredDate;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /** Bcrypt hash; SSN is never used for lookups or validation. */
    @Column(nullable = false, length = 60)
    private String ssn;

    @Column(name = "employment_status", nullable = false)
    private String employmentStatus;

    @Column(name = "employer_name")
    private String employerName;

    private String occupation;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus;

    @Column(name = "investment_experience", nullable = false)
    private String investmentExperience;

    // Initialised here because Hibernate inserts every mapped column, bypassing the DB defaults.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientSegment segment = ClientSegment.RETAIL;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Override
    public Set<Role> roles() {
        return Set.of(Role.CLIENT);
    }

    @Override
    public boolean canLogIn() {
        return accountStatus == null || accountStatus.canLogIn();
    }

    public ClientSegment getSegment() {
        return segment;
    }

    public void setSegment(ClientSegment segment) {
        this.segment = segment;
    }

    @Override
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    @Override
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    @Override
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    @Override
    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Integer getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getRegisteredDate() {
        return registeredDate;
    }

    public void setRegisteredDate(LocalDateTime registeredDate) {
        this.registeredDate = registeredDate;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getInvestmentExperience() {
        return investmentExperience;
    }

    public void setInvestmentExperience(String investmentExperience) {
        this.investmentExperience = investmentExperience;
    }
}
