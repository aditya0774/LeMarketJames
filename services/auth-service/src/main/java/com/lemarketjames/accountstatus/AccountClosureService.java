package com.lemarketjames.accountstatus;

import com.lemarketjames.auth.AuthService;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.error.ValidationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * A client closing their own account (BR-03). Allowed only once nothing on the account can still
 * move: no stocks held and no order in progress. Any cash stays on the closed account, never
 * deleted; operations pay it out, which is outside the platform (BRS 4.2).
 */
@Service
public class AccountClosureService {

    /** The reason the history shows for a client's own closing. */
    public static final String SELF_CLOSE_REASON = "Closed by the client";

    private final ClientStatusRepository lockedClients;
    private final AccountContents contents;
    private final AccountStatusService statuses;
    private final AuthService auth;

    public AccountClosureService(ClientStatusRepository lockedClients, AccountContents contents,
                                 AccountStatusService statuses, AuthService auth) {
        this.lockedClients = lockedClients;
        this.contents = contents;
        this.statuses = statuses;
        this.auth = auth;
    }

    /**
     * What is left on the caller's account and whether it may be closed now, so the trading app can
     * say what to do first, and how much cash stays, before asking for the password.
     */
    @Transactional(readOnly = true)
    public AccountContents.Contents check(String username) {
        return contents.of(username).orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
    }

    /**
     * Closes the caller's account. The client row stays locked until the transaction ends, so a
     * staff status change at the same moment waits for this one.
     *
     * @param username the signed-in client
     * @param password their password, asked again because closing cannot be undone by the client
     * @return what was left on the account; its cash stays there
     * @throws ValidationException     if the password is wrong
     * @throws AccountNotEmptyException if stocks or open orders remain
     */
    @Transactional
    public AccountContents.Contents close(String username, String password) {
        ClientEntity client = lockedClients.findLockedByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        if (password == null || password.isEmpty() || !auth.matchesPassword(password, client.getPassword())) {
            throw new ValidationException(Map.of("password", "The password is not correct."));
        }
        AccountContents.Contents left = check(username);
        if (!left.closable()) {
            throw new AccountNotEmptyException(left);
        }
        statuses.record(client, AccountStatus.CLOSED, SELF_CLOSE_REASON, username);
        return left;
    }
}
