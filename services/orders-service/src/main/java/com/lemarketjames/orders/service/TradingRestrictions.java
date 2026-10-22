package com.lemarketjames.orders.service;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.market.model.QuoteFreshness;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.orders.repository.InstrumentLocationRestrictionRepository;
import java.time.Clock;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

/** Shared placement/execution policy, driven by the platform contract settings. */
@Service
public class TradingRestrictions {
    private final PlatformSettings settings;
    private final AccountRepository accounts;
    private final AddressRepository addresses;
    private final InstrumentLocationRestrictionRepository instrumentRules;
    private final Clock clock;
    public TradingRestrictions(PlatformSettings settings, AccountRepository accounts, AddressRepository addresses,
            InstrumentLocationRestrictionRepository instrumentRules, Clock clock) {
        this.settings = settings; this.accounts = accounts; this.addresses = addresses;
        this.instrumentRules = instrumentRules; this.clock = clock;
    }
    /**
     * Whether the account's client lives where this instrument may not be traded (BR-05): a location
     * in the platform-wide list (every instrument) or one of the instrument's own rules
     * (instrument_location_restrictions), matched against a residential address's state or country.
     */
    public boolean locationRestricted(int accountId, int instrumentId) {
        var denied = new ArrayList<>(settings.getOrders().getRestrictedLocations());
        denied.addAll(instrumentRules.findLocationsByInstrumentId(instrumentId));
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
