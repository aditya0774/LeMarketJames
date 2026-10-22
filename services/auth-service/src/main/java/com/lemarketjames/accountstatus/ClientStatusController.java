package com.lemarketjames.accountstatus;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trading Ops' view and change of a client's account status (BR-03, contract C6). TRADING_OPS
 * only (SecurityConfig), and routed by the staff app only.
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/status")
public class ClientStatusController {

    private final AccountStatusService statuses;

    public ClientStatusController(AccountStatusService statuses) {
        this.statuses = statuses;
    }

    /** The client's status and its history, newest first. */
    @GetMapping
    public AccountStatusService.ClientStatusView status(@PathVariable int clientId) {
        return statuses.view(clientId);
    }

    /** Changes the status, recording the signed-in staff member and their reason. */
    @PutMapping
    public AccountStatusService.ClientStatusView change(@PathVariable int clientId,
                                                        @RequestBody StatusChangeRequest request,
                                                        Authentication staff) {
        return statuses.change(clientId, request.status(), request.reason(), staff.getName());
    }

    /** The body of a status change: the new status name and why. */
    public record StatusChangeRequest(String status, String reason) {
    }
}
