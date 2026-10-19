package com.lemarketjames.orders.service;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.market.model.QuoteFreshness;
import com.lemarketjames.market.model.QuoteSnapshot;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** Shared placement/execution policy, driven by the platform contract settings. */
@Service
public class TradingRestrictions {
    private final PlatformSettings settings;
    private final AccountRepository accounts;
    private final AddressRepository addresses;
    private final Clock clock;
    public TradingRestrictions(PlatformSettings settings, AccountRepository accounts, AddressRepository addresses, Clock clock) {
        this.settings = settings; this.accounts = accounts; this.addresses = addresses; this.clock = clock;
    }
    public boolean locationRestricted(int accountId) {
        var denied = settings.getOrders().getRestrictedLocations();
        if (denied.isEmpty()) return false;
        var account = accounts.findById(accountId).orElseThrow();
        return addresses.findByClientId(account.getClientId()).stream()
            .filter(a -> "RESIDENTIAL".equals(a.getAddressType()))
            .anyMatch(a -> denied.stream().anyMatch(location -> location.equalsIgnoreCase(a.getState())
                || location.equalsIgnoreCase(a.getCountry())));
    }
    public boolean stale(QuoteSnapshot quote) {
        return QuoteFreshness.isStale(quote, settings.getMarket().getStalenessLimit(), clock);
    }
}
