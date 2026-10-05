package com.lemarketjames.orders.service;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TradingRestrictions.
 * Tests platform policy validation for location restrictions and quote staleness.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradingRestrictions Tests")
class TradingRestrictionsTest {

    @Mock
    private PlatformSettings settings;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private TradingRestrictions restrictions;

    private static final Integer TEST_ACCOUNT_ID = 1;
    private static final Integer TEST_CLIENT_ID = 100;
    
    

    // Test configuration
    private PlatformSettings.Orders orderSettings;
    private PlatformSettings.Market marketSettings;

    @BeforeEach
    void setUp() {
        setupOrderSettings();
        setupMarketSettings();
    }

    // ========== Location Restriction Tests ==========

    @Test
    @DisplayName("Should return false when no locations are restricted")
    void testLocationRestricted_NoRestrictedLocations() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of());

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertFalse(restricted);
    }

    @Test
    @DisplayName("Should return false when account has no residential addresses")
    void testLocationRestricted_NoResidentialAddresses() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("NY"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "BUSINESS", "CA", "US")
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertFalse(restricted);
    }

    @Test
    @DisplayName("Should return false when residential address is not in restricted list")
    void testLocationRestricted_AddressNotRestricted() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("NY", "CA"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "TX", "US")
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertFalse(restricted);
    }

    @Test
    @DisplayName("Should return true when residential address state is restricted")
    void testLocationRestricted_StateRestricted() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("NY", "CA"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "NY", "US")
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertTrue(restricted);
    }

    @Test
    @DisplayName("Should return true when residential address country is restricted")
    void testLocationRestricted_CountryRestricted() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("US", "CA"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "Quebec", "CA")
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertTrue(restricted);
    }

    @Test
    @DisplayName("Should do case-insensitive matching for state restrictions")
    void testLocationRestricted_CaseInsensitiveState() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("ny"));  // lowercase
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "NY", "US")  // uppercase
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertTrue(restricted, "Should match case-insensitively");
    }

    @Test
    @DisplayName("Should do case-insensitive matching for country restrictions")
    void testLocationRestricted_CaseInsensitiveCountry() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("us"));  // lowercase
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "NY", "US")  // uppercase
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertTrue(restricted, "Should match case-insensitively");
    }

    @Test
    @DisplayName("Should ignore business addresses when checking restrictions")
    void testLocationRestricted_IgnoresBusinessAddresses() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("NY"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "BUSINESS", "NY", "US"),
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "TX", "US")
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertFalse(restricted, "Should only check RESIDENTIAL addresses");
    }

    @Test
    @DisplayName("Should return true when any residential address is restricted")
    void testLocationRestricted_MultipleResidentialAddresses() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("CA"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<AddressEntity> addresses = List.of(
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "TX", "US"),
            createTestAddress(TEST_CLIENT_ID, "RESIDENTIAL", "CA", "US")
        );
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(addresses);

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertTrue(restricted, "Should return true if any residential address matches");
    }

    @Test
    @DisplayName("Should throw when account not found")
    void testLocationRestricted_AccountNotFound() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("NY"));
        
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(Exception.class, () ->
            restrictions.locationRestricted(TEST_ACCOUNT_ID)
        );
    }

    @Test
    @DisplayName("Should handle empty address list from repository")
    void testLocationRestricted_EmptyAddresses() {
        // Arrange
        when(settings.getOrders()).thenReturn(orderSettings);
        orderSettings.setRestrictedLocations(List.of("NY"));
        
        AccountEntity account = createTestAccount();
        when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        when(addressRepository.findByClientId(TEST_CLIENT_ID)).thenReturn(List.of());

        // Act
        boolean restricted = restrictions.locationRestricted(TEST_ACCOUNT_ID);

        // Assert
        assertFalse(restricted);
    }

    // ========== Quote Staleness Tests ==========

    @Test
    @DisplayName("Should return false when quote is recent")
    void testStale_RecentQuote() {
        // Arrange
        setupClockToNow();
        when(settings.getMarket()).thenReturn(marketSettings);
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
        
        Instant now = clock.instant();
        Instant updated = now.minusSeconds(30);  // 30 seconds ago
        QuoteSnapshot quote = createTestQuote(updated);

        // Act
        boolean stale = restrictions.stale(quote);

        // Assert
        assertFalse(stale, "Recent quote should not be stale");
    }

    @Test
    @DisplayName("Should return true when quote exceeds staleness limit")
    void testStale_StaleQuote() {
        // Arrange
        setupClockToNow();
        when(settings.getMarket()).thenReturn(marketSettings);
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
        
        Instant now = clock.instant();
        Instant updated = now.minusSeconds(61);  // 61 seconds ago, exceeds 60-second limit
        QuoteSnapshot quote = createTestQuote(updated);

        // Act
        boolean stale = restrictions.stale(quote);

        // Assert
        assertTrue(stale, "Stale quote should be detected");
    }

    @Test
    @DisplayName("Should return true when quote has null timestamp")
    void testStale_NullTimestamp() {
        // Arrange
        when(settings.getMarket()).thenReturn(marketSettings);
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
        
        QuoteSnapshot quote = createTestQuoteWithNullTimestamp();

        // Act
        boolean stale = restrictions.stale(quote);

        // Assert
        assertTrue(stale, "Quote with null timestamp should be stale");
    }

    @Test
    @DisplayName("Should return false when quote is exactly at staleness limit")
    void testStale_EdgeCase_AtLimit() {
        // Arrange
        setupClockToNow();
        when(settings.getMarket()).thenReturn(marketSettings);
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
        
        Instant now = clock.instant();
        Instant updated = now.minusSeconds(60);  // Exactly 60 seconds ago
        QuoteSnapshot quote = createTestQuote(updated);

        // Act
        boolean stale = restrictions.stale(quote);

        // Assert
        assertFalse(stale, "Quote exactly at limit should not be stale");
    }

    @Test
    @DisplayName("Should return true when quote is one nanosecond past limit")
    void testStale_EdgeCase_JustPastLimit() {
        // Arrange
        setupClockToNow();
        when(settings.getMarket()).thenReturn(marketSettings);
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
        
        Instant now = clock.instant();
        Instant updated = now.minusSeconds(60).minusNanos(1);  // Just past the limit
        QuoteSnapshot quote = createTestQuote(updated);

        // Act
        boolean stale = restrictions.stale(quote);

        // Assert
        assertTrue(stale, "Quote past limit should be stale");
    }

    @Test
    @DisplayName("Should respect different staleness limits")
    void testStale_DifferentStalenessLimits() {
        // Arrange
        setupClockToNow();
        when(settings.getMarket()).thenReturn(marketSettings);
        
        Instant now = clock.instant();
        Instant updated = now.minusSeconds(30);
        QuoteSnapshot quote = createTestQuote(updated);

        // Case 1: 60-second limit
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
        assertFalse(restrictions.stale(quote), "30s old quote should be fresh with 60s limit");

        // Case 2: 10-second limit
        marketSettings.setStalenessLimit(Duration.ofSeconds(10));
        assertTrue(restrictions.stale(quote), "30s old quote should be stale with 10s limit");
    }

    // ========== Helper Methods ==========

    private void setupOrderSettings() {
        orderSettings = new PlatformSettings.Orders();
        orderSettings.setRestrictedLocations(new ArrayList<>());
    }

    private void setupMarketSettings() {
        marketSettings = new PlatformSettings.Market();
        marketSettings.setStalenessLimit(Duration.ofSeconds(60));
    }

    private void setupClockToNow() {
        Instant fixedTime = Instant.parse("2026-01-15T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedTime, ZoneId.systemDefault());
        when(clock.instant()).thenReturn(fixedClock.instant());
    }

    private AccountEntity createTestAccount() {
        AccountEntity account = new AccountEntity();
        account.setClientId(TEST_CLIENT_ID);
        account.setCashBalance(BigDecimal.valueOf(10000));
        account.setCurrency("USD");
        return account;
    }

    private AddressEntity createTestAddress(Integer clientId, String addressType, String state, String country) {
        AddressEntity address = new AddressEntity();
        address.setClientId(clientId);
        address.setAddressType(addressType);
        address.setState(state);
        address.setCountry(country);
        address.setStreetAddress("123 Main St");
        address.setCity("Springfield");
        return address;
    }

    private QuoteSnapshot createTestQuote(Instant lastUpdated) {
        return new QuoteSnapshot(
            createTestInstrument(),
            100.0,
            99.50,
            100.50,
            99.0,
            101.0,
            98.0,
            99.0,
            1000000L,
            lastUpdated,
            LocalDate.now()
        );
    }

    private QuoteSnapshot createTestQuoteWithNullTimestamp() {
        return new QuoteSnapshot(
            createTestInstrument(),
            100.0,
            99.50,
            100.50,
            99.0,
            101.0,
            98.0,
            99.0,
            1000000L,
            null,  // null timestamp
            LocalDate.now()
        );
    }

    private MarketInstrument createTestInstrument() {
        return new MarketInstrument(
            1,              // instrumentId
            "AAPL",         // ticker
            "Apple Inc.",   // name
            "EQUITY",       // assetClass
            "USD",          // currency
            "US",           // location
            100.0,          // initialPrice
            0.1,            // drift
            0.2,            // volatility
            0.7,            // marketCorrelation
            10.0,           // spreadBps
            16000000000L,   // sharesOutstanding
            50000000L,      // avgDailyVolume
            6.5,            // earningsPerShare
            0.92            // dividendPerShare
        );
    }
}
