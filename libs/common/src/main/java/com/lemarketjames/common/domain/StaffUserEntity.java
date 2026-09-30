package com.lemarketjames.common.domain;

import com.lemarketjames.common.security.Role;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Set;

/**
 * Maps to the `staff_users` table (database/schema/010): an internal user with exactly one staff
 * {@link Role}. Staff are not clients, so they have no trading account (contract C7).
 */
@Entity
@Table(name = "staff_users")
public class StaffUserEntity implements LoginAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Integer staffId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Integer getStaffId() {
        return staffId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    /** Staff roles only; a staff user can never be a {@link Role#CLIENT}. */
    public void setRole(Role role) {
        if (role == Role.CLIENT) {
            throw new IllegalArgumentException("Staff users cannot have the CLIENT role");
        }
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public Set<Role> roles() {
        return Set.of(role);
    }

    @Override
    public boolean canLogIn() {
        return active;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
