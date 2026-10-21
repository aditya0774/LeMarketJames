package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.RegisterRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules a registration must meet (BR-01, contract C6). The server is authoritative: a client
 * that skips the form (curl, a script, a modified browser) gets the same rules. The trading app's
 * form (apps/frontend/.../register/register.schema.ts) mirrors them, with the same messages, so a
 * mistake is caught before the request is sent. Change both together.
 *
 * <p>Each field reports only its first broken rule, as the form does, so the caller gets one
 * message per field in a {@code 400 { errors }} body.
 */
final class RegistrationRules {

    /** Letters, spaces, hyphens and apostrophes ("Mary-Jane O'Neil"), as the form's name fields. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z' -]{2,60}");
    /**
     * The form sends first, middle and last name joined with spaces, each up to 60 characters of
     * the name characters above.
     */
    private static final Pattern FULL_NAME = Pattern.compile("[A-Za-z' -]{2,182}");
    /** Letters, digits, spaces and . , # - ("123 Main St #4B"). */
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9\\s.,#-]{2,100}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]+");
    /** The format the form types for the user: (XXX) XXX-XXXX. */
    private static final Pattern PHONE = Pattern.compile("\\(\\d{3}\\) \\d{3}-\\d{4}");
    private static final Pattern SSN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
    private static final Pattern ZIP = Pattern.compile("\\d{5}");
    /** One @, no spaces, ending in .com. */
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.com", Pattern.CASE_INSENSITIVE);

    private static final BigDecimal MINIMUM_DEPOSIT = new BigDecimal("5000");
    /** A client may call themselves experienced only with at least this much at stake. */
    private static final BigDecimal EXPERIENCED_MINIMUM_DEPOSIT = new BigDecimal("100000");
    /** The form offers "beginner" and "experienced"; the database accepts only these. */
    private static final Set<String> EXPERIENCE = Set.of("beginner", "experienced");
    /** What the form's "Employed" and "Not employed" choices send. */
    private static final Set<String> EMPLOYMENT = Set.of("EMPLOYED", "UNEMPLOYED");
    /** The form requires an age above this, in whole years. */
    private static final int MINIMUM_AGE_EXCLUSIVE = 18;

    private static final String PASSWORD_MESSAGE = "Password must be at least 8 characters and include an "
            + "uppercase letter, a lowercase letter, and a special character.";

    private final Clock clock;

    RegistrationRules(Clock clock) {
        this.clock = clock;
    }

    /** Every field that breaks a rule, mapped to its message; empty when the request is valid. */
    Map<String, String> check(RegisterRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        username(errors, request.getUsername());
        password(errors, request.getPassword());
        text(errors, "email", request.getEmail(), "Email is required.", EMAIL, "Email must contain @ and end in .com.");
        text(errors, "fullName", request.getFullName(), "Full name is required.", FULL_NAME,
                "Name may only contain letters, spaces, hyphens, and apostrophes.");
        text(errors, "streetAddress", request.getStreetAddress(), "Street address is required.", ADDRESS,
                "Street address may only contain letters, numbers, spaces, and . , # -.");
        optionalText(errors, "apartment", request.getApartment(), ADDRESS,
                "Apartment may only contain letters, numbers, spaces, and . , # -.");
        text(errors, "city", request.getCity(), "City is required.", NAME,
                "City may only contain letters, spaces, hyphens, and apostrophes.");
        text(errors, "state", request.getState(), "State is required.", NAME,
                "State may only contain letters, spaces, hyphens, and apostrophes.");
        text(errors, "zipCode", request.getZipCode(), "ZIP code is required.", ZIP, "ZIP code must be exactly 5 digits.");
        text(errors, "ssn", request.getSsn(), "SSN is required.", SSN, "SSN must be 9 digits in the format xxx-xx-xxxx.");
        text(errors, "phoneNumber", request.getPhoneNumber(), "Phone is required.", PHONE,
                "Phone number must be 10 digits.");
        dateOfBirth(errors, request.getDateOfBirth());
        deposit(errors, request.getInitialDeposit());
        oneOf(errors, "employmentStatus", upper(request.getEmploymentStatus()), "Employment status is required.",
                EMPLOYMENT, "Employment status must be EMPLOYED or UNEMPLOYED.");
        oneOf(errors, "investmentExperience", lower(request.getInvestmentExperience()), "Investment experience is required.",
                EXPERIENCE, "Investment experience must be beginner or experienced.");
        experiencedNeedsDeposit(errors, request);
        return errors;
    }

    private static void text(Map<String, String> errors, String field, String value, String required,
            Pattern pattern, String invalid) {
        if (value == null || value.isBlank()) {
            errors.put(field, required);
        } else if (!pattern.matcher(value.trim()).matches()) {
            errors.put(field, invalid);
        }
    }

    // Length first, then characters, with the form's two messages.
    private static void username(Map<String, String> errors, String value) {
        if (value == null || value.isBlank()) {
            errors.put("username", "Username is required.");
        } else if (value.trim().length() < 3 || value.trim().length() > 20) {
            errors.put("username", "Username must be between 3 and 20 characters.");
        } else if (!USERNAME.matcher(value.trim()).matches()) {
            errors.put("username", "Username may only contain letters, numbers, underscores, and hyphens.");
        }
    }

    private static void optionalText(Map<String, String> errors, String field, String value, Pattern pattern,
            String invalid) {
        if (value != null && !value.isBlank() && !pattern.matcher(value.trim()).matches()) {
            errors.put(field, invalid);
        }
    }

    // Not trimmed: a space is a character the user chose, as in the form.
    private static void password(Map<String, String> errors, String password) {
        if (password == null || password.isEmpty()) {
            errors.put("password", "Password is required.");
        } else if (password.length() < 8 || !password.matches("(?s).*[A-Z].*")
                || !password.matches("(?s).*[a-z].*") || !password.matches("(?s).*[^A-Za-z0-9].*")) {
            errors.put("password", PASSWORD_MESSAGE);
        }
    }

    private void dateOfBirth(Map<String, String> errors, LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            errors.put("dateOfBirth", "Date of birth is required.");
        } else if (Period.between(dateOfBirth, LocalDate.now(clock)).getYears() <= MINIMUM_AGE_EXCLUSIVE) {
            errors.put("dateOfBirth", "You must be older than 18 to register.");
        }
    }

    private static void deposit(Map<String, String> errors, BigDecimal deposit) {
        if (deposit == null) {
            errors.put("initialDeposit", "Initial deposit amount is required.");
        } else if (deposit.compareTo(MINIMUM_DEPOSIT) < 0) {
            errors.put("initialDeposit", "Initial deposit must be at least $5,000.");
        }
    }

    // The service stores these normalized (upper or lower case), so any case is accepted here.
    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void oneOf(Map<String, String> errors, String field, String normalized, String required,
            Set<String> allowed, String invalid) {
        if (normalized == null || normalized.isEmpty()) {
            errors.put(field, required);
        } else if (!allowed.contains(normalized)) {
            errors.put(field, invalid);
        }
    }

    private static void experiencedNeedsDeposit(Map<String, String> errors, RegisterRequest request) {
        BigDecimal deposit = request.getInitialDeposit();
        if (!errors.containsKey("investmentExperience")
                && "experienced".equals(lower(request.getInvestmentExperience()))
                && (deposit == null || deposit.compareTo(EXPERIENCED_MINIMUM_DEPOSIT) < 0)) {
            errors.put("investmentExperience", "Experienced requires an initial deposit of $100,000 or more.");
        }
    }
}
