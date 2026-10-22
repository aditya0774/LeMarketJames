package com.lemarketjames.accountstatus;

import com.lemarketjames.common.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One change to a client's account status (database/schema/027): who changed it, from what, to
 * what and why. Rows are only ever added, so there are no setters; the database lets the services
 * read and insert them, nothing more.
 */
@Entity
@Table(name = "account_status_changes")
public class AccountStatusChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_id")
    private Long changeId;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private AccountStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AccountStatus newStatus;

    @Column(nullable = false, length = AccountStatusService.MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected AccountStatusChangeEntity() {
        // for JPA
    }

    public AccountStatusChangeEntity(Integer clientId, AccountStatus oldStatus, AccountStatus newStatus,
                                     String reason, String changedBy, Instant changedAt) {
        this.clientId = clientId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getChangeId() {
        return changeId;
    }

    public Integer getClientId() {
        return clientId;
    }

    public AccountStatus getOldStatus() {
        return oldStatus;
    }

    public AccountStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
