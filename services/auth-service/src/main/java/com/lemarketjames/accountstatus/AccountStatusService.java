package com.lemarketjames.accountstatus;

import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.error.ValidationException;
import com.lemarketjames.common.security.SecurityEventLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Changes a client's account status and keeps its history (BR-03, contract C7): Trading Ops
 * suspend, reinstate, close or expire an account with a reason, and every change, the client's own
 * closing included, gets a row in {@code account_status_changes} in the same transaction.
 *
 * <p>Nothing here ends a session directly: every service checks the status on each request
 * (JpaAccountAccessCheck), so a client who loses access is refused on their next request.
 */
@Service
public class AccountStatusService {

    /** The longest reason the history keeps: the length of {@code account_status_changes.reason} (027). */
    public static final int MAX_REASON_LENGTH = 500;

    private final ClientRepository clients;
    private final ClientStatusRepository lockedClients;
    private final AccountStatusChangeRepository changes;
    private final Clock clock;

    @Autowired
    public AccountStatusService(ClientRepository clients, ClientStatusRepository lockedClients,
                                AccountStatusChangeRepository changes) {
        this(clients, lockedClients, changes, Clock.systemUTC());
    }

    /** Takes the clock changes are stamped with, so tests can fix the time. */
    AccountStatusService(ClientRepository clients, ClientStatusRepository lockedClients,
                         AccountStatusChangeRepository changes, Clock clock) {
        this.clients = clients;
        this.lockedClients = lockedClients;
        this.changes = changes;
        this.clock = clock;
    }

    /**
     * A client's status and its history, newest change first.
     *
     * @throws ClientNotFoundException if no client has this id
     */
    @Transactional(readOnly = true)
    public ClientStatusView view(int clientId) {
        return viewOf(clients.findById(clientId).orElseThrow(ClientNotFoundException::new));
    }

    /**
     * Trading Ops changes a client's status.
     *
     * @param clientId  the client
     * @param status    the new status, an {@link AccountStatus} name (any case)
     * @param reason    why, kept in the history
     * @param changedBy the staff member's username
     * @return the client's status and history after the change
     * @throws ClientNotFoundException if no client has this id
     * @throws ValidationException     if the status or reason is missing or wrong, or the status is unchanged
     */
    @Transactional
    public ClientStatusView change(int clientId, String status, String reason, String changedBy) {
        AccountStatus next = parseStatus(status);
        String why = checkReason(reason);
        ClientEntity client = lockedClients.findLockedById(clientId).orElseThrow(ClientNotFoundException::new);
        if (client.getAccountStatus() == next) {
            throw new ValidationException(Map.of("status", "The account is already " + next.name() + "."));
        }
        record(client, next, why, changedBy);
        return viewOf(client);
    }

    /**
     * Sets the client's status and writes its history row. Call it inside a transaction, on a
     * client loaded with its row locked, and only for a real change.
     *
     * <p>It also marks every session issued so far as signed out. A status other than ACTIVE
     * refuses them anyway, but a later reinstatement must not bring those old sessions back.
     */
    public void record(ClientEntity client, AccountStatus next, String reason, String changedBy) {
        AccountStatus previous = client.getAccountStatus();
        // PostgreSQL keeps microseconds; the reply then shows the time the history will show.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        client.setAccountStatus(next);
        Instant invalidatedAt = client.getSessionInvalidatedAt();
        client.setSessionInvalidatedAt(invalidatedAt == null || invalidatedAt.isBefore(now) ? now : invalidatedAt);
        clients.save(client);
        changes.save(new AccountStatusChangeEntity(client.getClientId(), previous, next, reason, changedBy, now));
        SecurityEventLog.accountStatusChanged(client.getUsername(), previous.name(), next.name(), changedBy, now);
    }

    private ClientStatusView viewOf(ClientEntity client) {
        List<StatusChangeView> history = changes.findByClientIdOrderByChangedAtDescChangeIdDesc(client.getClientId())
                .stream()
                .map(change -> new StatusChangeView(change.getOldStatus(), change.getNewStatus(), change.getReason(),
                        change.getChangedBy(), change.getChangedAt()))
                .toList();
        return new ClientStatusView(client.getClientId(), client.getFullName(), client.getAccountStatus(), history);
    }

    private static AccountStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ValidationException(Map.of("status", "Choose a status."));
        }
        try {
            return AccountStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            // Named from the enum, so the message never falls behind the list of statuses.
            String allowed = Arrays.stream(AccountStatus.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new ValidationException(Map.of("status", "Choose one of " + allowed + "."));
        }
    }

    private static String checkReason(String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new ValidationException(Map.of("reason", "Give a reason for the change."));
        }
        if (why.length() > MAX_REASON_LENGTH) {
            throw new ValidationException(Map.of("reason",
                    "Keep the reason to " + MAX_REASON_LENGTH + " characters or fewer."));
        }
        return why;
    }

    /** What Trading Ops see about a client's standing; no personal details beyond the name trade search shows. */
    public record ClientStatusView(int clientId, String fullName, AccountStatus status, List<StatusChangeView> changes) {
    }

    /** One history row as the staff app shows it. */
    public record StatusChangeView(AccountStatus oldStatus, AccountStatus newStatus, String reason,
                                   String changedBy, Instant changedAt) {
    }
}
