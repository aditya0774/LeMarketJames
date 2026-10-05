package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.InternalHoldingsValidationRequest;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.service.HoldingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for HoldingsValidationInternalController.
 * Tests server-to-server SELL order holdings validation endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("HoldingsValidationInternalController Tests")
class HoldingsValidationInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HoldingsService holdingsService;

    private static final Integer TEST_ACCOUNT_ID = 1;
    private static final String TEST_USERNAME = "testuser";
    private static final Integer TEST_INSTRUMENT_ID = 5;
    private static final BigDecimal TEST_QUANTITY = BigDecimal.valueOf(100);
    private static final String ENDPOINT = "/internal/holdings/validate";

    @BeforeEach
    void setUp() {
        // No special setup needed
    }

    // ========== Happy Path Tests ==========

    @Test
    @DisplayName("Should return 200 OK when holdings are sufficient")
    void testValidate_SufficientHoldings() throws Exception {
        // Arrange
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Should call HoldingsService.validateSufficientHoldings with correct parameters")
    void testValidate_CallsServiceWithCorrectParams() throws Exception {
        // Arrange
        doNothing().when(holdingsService).validateSufficientHoldings(
            TEST_ACCOUNT_ID, TEST_USERNAME, TEST_INSTRUMENT_ID, TEST_QUANTITY);

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk());

        // Assert - verify service was called with exact parameters
        verify(holdingsService).validateSufficientHoldings(
            TEST_ACCOUNT_ID, TEST_USERNAME, TEST_INSTRUMENT_ID, TEST_QUANTITY);
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Should return 400 when holdings are insufficient")
    void testValidate_InsufficientHoldings() throws Exception {
        // Arrange
        doThrow(new InsufficientHoldingsException("Not enough shares"))
            .when(holdingsService).validateSufficientHoldings(
                anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error", notNullValue()))
            .andExpect(jsonPath("$.error", containsString("Not enough shares")));
    }

    @Test
    @DisplayName("Should include error message in response on insufficient holdings")
    void testValidate_IncludesErrorMessage() throws Exception {
        // Arrange
        String errorMsg = "Insufficient holdings for AAPL";
        doThrow(new InsufficientHoldingsException(errorMsg))
            .when(holdingsService).validateSufficientHoldings(
                anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error", is(errorMsg)));
    }

    // ========== Request Validation Tests ==========

    @Test
    @DisplayName("Should accept request with all required fields")
    void testValidate_AllRequiredFields() throws Exception {
        // Arrange
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(123);
        request.setUsername("alice");
        request.setInstrumentId(45);
        request.setSellQuantity(BigDecimal.valueOf(50));

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle zero quantity")
    void testValidate_ZeroQuantity() throws Exception {
        // Arrange
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(BigDecimal.ZERO);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle large quantities")
    void testValidate_LargeQuantity() throws Exception {
        // Arrange
        BigDecimal largeQty = BigDecimal.valueOf(999999999.9999);
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(largeQty);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle fractional shares")
    void testValidate_FractionalShares() throws Exception {
        // Arrange
        BigDecimal fractional = BigDecimal.valueOf(0.5);
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(fractional);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should use correct endpoint path /internal/holdings/validate")
    void testValidate_EndpointPath() throws Exception {
        // Arrange
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act & Assert - posting to /internal/holdings/validate should work
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should only accept POST requests")
    void testValidate_PostMethodOnly() throws Exception {
        // Act & Assert - GET should fail
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(ENDPOINT))
            .andExpect(status().isMethodNotAllowed());
    }

    // ========== Response Structure Tests ==========

    @Test
    @DisplayName("Should return JSON response with success field")
    void testValidate_ResponseStructure_Success() throws Exception {
        // Arrange
        doNothing().when(holdingsService).validateSufficientHoldings(
            anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", aMapWithSize(1)))
            .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Should return JSON response with success and error fields on failure")
    void testValidate_ResponseStructure_Error() throws Exception {
        // Arrange
        doThrow(new InsufficientHoldingsException("Error message"))
            .when(holdingsService).validateSufficientHoldings(
                anyInt(), anyString(), anyInt(), any(BigDecimal.class));

        InternalHoldingsValidationRequest request = new InternalHoldingsValidationRequest();
        request.setAccountId(TEST_ACCOUNT_ID);
        request.setUsername(TEST_USERNAME);
        request.setInstrumentId(TEST_INSTRUMENT_ID);
        request.setSellQuantity(TEST_QUANTITY);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", aMapWithSize(2)))
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error", notNullValue()));
    }

    // ========== Helper Methods ==========

    private String toJson(InternalHoldingsValidationRequest request) {
        return String.format(
            "{\"accountId\":%d,\"username\":\"%s\",\"instrumentId\":%d,\"sellQuantity\":%s}",
            request.getAccountId(),
            request.getUsername(),
            request.getInstrumentId(),
            request.getSellQuantity()
        );
    }
}
