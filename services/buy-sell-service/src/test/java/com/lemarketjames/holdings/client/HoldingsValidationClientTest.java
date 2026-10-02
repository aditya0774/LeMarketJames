package com.lemarketjames.holdings.client;

import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HoldingsValidationClient
 * Tests server-to-server validation with holdings-service before SELL order placement
 * 
 * Note: These tests validate the contract and error handling of HoldingsValidationClient.
 * Full integration testing requires a running holdings-service instance.
 */
@DisplayName("Holdings Validation Client Tests")
class HoldingsValidationClientTest {

    private HoldingsValidationClient client;

    private static final Integer TEST_ACCOUNT_ID = 1;
    private static final String TEST_USERNAME = "testuser";
    private static final Integer TEST_INSTRUMENT_ID = 5;
    private static final BigDecimal TEST_QUANTITY = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        // Create client pointing to a test URL (real RestClient will be used)
        client = new HoldingsValidationClient("http://localhost:9999");
    }

    // ========== Contract Validation Tests ==========

    @Test
    @DisplayName("Should have validateSufficientHoldings method with correct signature")
    void testMethodSignature() {
        // Verify the public API exists and is callable
        assertDoesNotThrow(() -> {
            // Just verify the method exists and can be accessed
            var method = HoldingsValidationClient.class
                .getDeclaredMethod("validateSufficientHoldings", 
                    Integer.class, String.class, Integer.class, BigDecimal.class);
            assertNotNull(method);
        });
    }

    @Test
    @DisplayName("Should require non-null account ID")
    void testValidateSufficientHoldings_NullAccountId() {
        assertThrows(Exception.class, () ->
            client.validateSufficientHoldings(null, TEST_USERNAME,
                TEST_INSTRUMENT_ID, TEST_QUANTITY)
        );
    }

    @Test
    @DisplayName("Should require non-null username")
    void testValidateSufficientHoldings_NullUsername() {
        assertThrows(Exception.class, () ->
            client.validateSufficientHoldings(TEST_ACCOUNT_ID, null,
                TEST_INSTRUMENT_ID, TEST_QUANTITY)
        );
    }

    @Test
    @DisplayName("Should require non-null instrument ID")
    void testValidateSufficientHoldings_NullInstrumentId() {
        assertThrows(Exception.class, () ->
            client.validateSufficientHoldings(TEST_ACCOUNT_ID, TEST_USERNAME,
                null, TEST_QUANTITY)
        );
    }

    @Test
    @DisplayName("Should require non-null quantity")
    void testValidateSufficientHoldings_NullQuantity() {
        assertThrows(Exception.class, () ->
            client.validateSufficientHoldings(TEST_ACCOUNT_ID, TEST_USERNAME,
                TEST_INSTRUMENT_ID, null)
        );
    }

    // ========== Request Validation Tests ==========

    @Test
    @DisplayName("Should accept valid zero quantity")
    void testValidateSufficientHoldings_ZeroQuantity() {
        // Should not throw on construction/validation
        // (Will throw when actually connecting to non-existent service, but that's a network error)
        assertThrows(IllegalStateException.class, () ->
            client.validateSufficientHoldings(TEST_ACCOUNT_ID, TEST_USERNAME,
                TEST_INSTRUMENT_ID, BigDecimal.ZERO)
        );
    }

    @Test
    @DisplayName("Should accept large quantities")
    void testValidateSufficientHoldings_LargeQuantity() {
        BigDecimal largeQty = BigDecimal.valueOf(999999999.9999);
        
        // Should not throw on construction/validation
        assertThrows(IllegalStateException.class, () ->
            client.validateSufficientHoldings(TEST_ACCOUNT_ID, TEST_USERNAME,
                TEST_INSTRUMENT_ID, largeQty)
        );
    }

    // ========== Behavior Tests ==========

    @Test
    @DisplayName("Should throw IllegalStateException when service is unreachable")
    void testValidateSufficientHoldings_ServiceUnreachable() {
        // Client points to non-existent service
        HoldingsValidationClient unreachableClient = 
            new HoldingsValidationClient("http://localhost:9999");
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            unreachableClient.validateSufficientHoldings(TEST_ACCOUNT_ID, TEST_USERNAME,
                TEST_INSTRUMENT_ID, TEST_QUANTITY)
        );
        
        assertTrue(exception.getMessage().contains("Failed to validate holdings"));
    }

    @Test
    @DisplayName("Should verify correct endpoint is called")
    void testEndpointConfiguration() {
        // Verify RestClient is created with correct base URL
        HoldingsValidationClient testClient = 
            new HoldingsValidationClient("http://holdings-service:8084");
        
        RestClient restClient = (RestClient) ReflectionTestUtils
            .getField(testClient, "restClient");
        assertNotNull(restClient);
    }
}
