package com.lemarketjames.common.domain;

import java.time.Instant;

/** The account state needed to decide whether a JWT may still be used. */
public record AccountAccessState(AccountStatus accountStatus, Boolean active, Instant sessionInvalidatedAt) {
}