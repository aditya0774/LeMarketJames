package com.lemarketjames.watchlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One stock on one client's watchlist. Holds ids only, with no relation to the account or
 * instrument entities, so the feature reads nothing beyond the ticker it shows.
 */
@Entity
@Table(name = "watchlist", uniqueConstraints = @UniqueConstraint(
        name = "watchlist_account_instrument_uq", columnNames = {"account_id", "instrument_id"}))
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watchlist_id")
    private Integer id;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected WatchlistEntry() {
        // for JPA
    }

    public WatchlistEntry(Integer accountId, Integer instrumentId, Instant addedAt) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.addedAt = addedAt;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
