package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.RegisterRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-01: the server applies the same registration rules as the trading app's form
 * (register.schema.ts), with the same messages, whatever sends the request.
 */
class RegistrationRulesTest {

    /** "Today" for the age rule. */
    static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    final RegistrationRules rules = new RegistrationRules(
            Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));

    /** What the form sends for a valid sign-up. */
    static RegisterRequest valid() {
        RegisterRequest request = new RegisterRequest("jane_doe", "Pass123!", "jane@example.com", "Jane Mary Doe");
        request.setStreetAddress("123 Main St #4B");
        request.setApartment("");
        request.setCity("Springfield");
        request.setState("Illinois");
        request.setZipCode("62701");
        request.setCountry("US");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(new BigDecimal("5000"));
        request.setInvestmentExperience("beginner");
        request.setEmploymentStatus("EMPLOYED");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        return request;
    }

    private Map<String, String> errorsAfter(Consumer<RegisterRequest> change) {
        RegisterRequest request = valid();
        change.accept(request);
        return rules.check(request);
    }

    private void assertOnly(String field, String message, Consumer<RegisterRequest> change) {
        assertEquals(Map.of(field, message), errorsAfter(change));
    }

    @Test
    void aValidRequestHasNoErrors() {
        assertTrue(rules.check(valid()).isEmpty());
    }

    @Test
    void everyFieldIsRequired() {
        Map<String, String> errors = rules.check(new RegisterRequest());
        for (String field : new String[] {"username", "password", "email", "fullName", "streetAddress", "city",
                "state", "zipCode", "ssn", "phoneNumber", "dateOfBirth", "initialDeposit", "employmentStatus",
                "investmentExperience"}) {
            assertTrue(errors.containsKey(field), field);
        }
        assertEquals("Initial deposit amount is required.", errors.get("initialDeposit"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pass123", "password!", "PASSWORD!", "Password1", "Pa1!"})
    void aPasswordNeedsEightCharactersUpperLowerAndSpecial(String password) {
        assertOnly("password", "Password must be at least 8 characters and include an uppercase letter, "
                + "a lowercase letter, and a special character.", r -> r.setPassword(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jane@example.org", "jane example.com", "jane@@example.com", "janeexample.com"})
    void anEmailNeedsOneAtAndDotCom(String email) {
        assertOnly("email", "Email must contain @ and end in .com.", r -> r.setEmail(email));
    }

    @Test
    void anEmailIsCaseInsensitive() {
        assertTrue(errorsAfter(r -> r.setEmail("Jane@Example.COM")).isEmpty());
    }

    @Test
    void aUsernameIsThreeToTwentyOfLettersDigitsUnderscoresHyphens() {
        assertOnly("username", "Username must be between 3 and 20 characters.", r -> r.setUsername("jd"));
        assertOnly("username", "Username must be between 3 and 20 characters.", r -> r.setUsername("a".repeat(21)));
        // The email is not a username: the form collects its own.
        assertOnly("username", "Username may only contain letters, numbers, underscores, and hyphens.",
                r -> r.setUsername("jane@ex.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5551234567", "555-123-4567", "(555)123-4567", "(555) 123-456"})
    void aPhoneNumberIsInTheFormsFormat(String phone) {
        assertOnly("phoneNumber", "Phone number must be 10 digits.", r -> r.setPhoneNumber(phone));
    }

    @Test
    void ssnZipAndAddressFollowTheirFormats() {
        assertOnly("ssn", "SSN must be 9 digits in the format xxx-xx-xxxx.", r -> r.setSsn("123456789"));
        assertOnly("zipCode", "ZIP code must be exactly 5 digits.", r -> r.setZipCode("0211"));
        assertOnly("streetAddress", "Street address may only contain letters, numbers, spaces, and . , # -.",
                r -> r.setStreetAddress("123 Main St; DROP"));
        assertOnly("apartment", "Apartment may only contain letters, numbers, spaces, and . , # -.",
                r -> r.setApartment("Flat @ 2"));
    }

    @Test
    void namesCityAndStateAreLettersSpacesHyphensApostrophes() {
        assertTrue(errorsAfter(r -> r.setFullName("Mary-Jane O'Neil")).isEmpty());
        assertOnly("fullName", "Name may only contain letters, spaces, hyphens, and apostrophes.",
                r -> r.setFullName("E2E Tester"));
        assertOnly("city", "City may only contain letters, spaces, hyphens, and apostrophes.", r -> r.setCity("B0ston"));
        assertOnly("state", "State may only contain letters, spaces, hyphens, and apostrophes.", r -> r.setState("I"));
    }

    @Test
    void aClientMustBeOlderThanEighteen() {
        // Exactly 18 today is not older than 18, as on the form.
        assertOnly("dateOfBirth", "You must be older than 18 to register.",
                r -> r.setDateOfBirth(TODAY.minusYears(18)));
        assertOnly("dateOfBirth", "You must be older than 18 to register.",
                r -> r.setDateOfBirth(TODAY.minusYears(19).plusDays(1)));
        assertTrue(errorsAfter(r -> r.setDateOfBirth(TODAY.minusYears(19))).isEmpty());
    }

    @Test
    void theInitialDepositIsAtLeastFiveThousand() {
        assertOnly("initialDeposit", "Initial deposit must be at least $5,000.",
                r -> r.setInitialDeposit(new BigDecimal("4999.99")));
    }

    @Test
    void experiencedNeedsAHundredThousand() {
        assertOnly("investmentExperience", "Experienced requires an initial deposit of $100,000 or more.",
                r -> r.setInvestmentExperience("experienced"));
        assertTrue(errorsAfter(r -> {
            r.setInvestmentExperience("experienced");
            r.setInitialDeposit(new BigDecimal("100000"));
        }).isEmpty());
    }

    @Test
    void experienceAndEmploymentAreTheFormsChoicesInAnyCase() {
        assertTrue(errorsAfter(r -> {
            r.setInvestmentExperience("Beginner");
            r.setEmploymentStatus("unemployed");
        }).isEmpty());
        assertOnly("investmentExperience", "Investment experience must be beginner or experienced.",
                r -> r.setInvestmentExperience("expert"));
        assertOnly("employmentStatus", "Employment status must be EMPLOYED or UNEMPLOYED.",
                r -> r.setEmploymentStatus("RETIRED"));
    }
}
