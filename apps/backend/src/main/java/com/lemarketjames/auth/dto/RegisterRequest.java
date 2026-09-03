package com.lemarketjames.auth.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data transfer object for user registration requests.
 * Contains comprehensive user information needed for account creation.
 */
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String streetAddress;
    private String apartment;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String ssn;
    private BigDecimal initialDeposit;
    private String investmentExperience;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private Boolean termsAccepted;

    /**
     * Default constructor for deserialization.
     */
    public RegisterRequest() {
    }

    /**
     * Constructs a RegisterRequest with basic user information.
     *
     * @param username the desired username
     * @param password the user's password
     * @param email the user's email address
     * @param fullName the user's full name
     */
    public RegisterRequest(String username, String password, String email, String fullName) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets the email address.
     *
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address.
     *
     * @param email the email to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gets the full name.
     *
     * @return the full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full name.
     *
     * @param fullName the full name to set
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Gets the street address.
     *
     * @return the street address
     */
    public String getStreetAddress() {
        return streetAddress;
    }

    /**
     * Sets the street address.
     *
     * @param streetAddress the street address to set
     */
    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    /**
     * Gets the apartment number.
     *
     * @return the apartment
     */
    public String getApartment() {
        return apartment;
    }

    /**
     * Sets the apartment number.
     *
     * @param apartment the apartment to set
     */
    public void setApartment(String apartment) {
        this.apartment = apartment;
    }

    /**
     * Gets the city.
     *
     * @return the city
     */
    public String getCity() {
        return city;
    }

    /**
     * Sets the city.
     *
     * @param city the city to set
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * Gets the state.
     *
     * @return the state
     */
    public String getState() {
        return state;
    }

    /**
     * Sets the state.
     *
     * @param state the state to set
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Gets the ZIP code.
     *
     * @return the ZIP code
     */
    public String getZipCode() {
        return zipCode;
    }

    /**
     * Sets the ZIP code.
     *
     * @param zipCode the ZIP code to set
     */
    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    /**
     * Gets the country.
     *
     * @return the country
     */
    public String getCountry() {
        return country;
    }

    /**
     * Sets the country.
     *
     * @param country the country to set
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * Gets the social security number.
     *
     * @return the SSN
     */
    public String getSsn() {
        return ssn;
    }

    /**
     * Sets the social security number.
     *
     * @param ssn the SSN to set
     */
    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    /**
     * Gets the initial deposit amount.
     *
     * @return the initial deposit
     */
    public BigDecimal getInitialDeposit() {
        return initialDeposit;
    }

    /**
     * Sets the initial deposit amount.
     *
     * @param initialDeposit the initial deposit to set
     */
    public void setInitialDeposit(BigDecimal initialDeposit) {
        this.initialDeposit = initialDeposit;
    }

    /**
     * Gets the investment experience level.
     *
     * @return the investment experience
     */
    public String getInvestmentExperience() {
        return investmentExperience;
    }

    /**
     * Sets the investment experience level.
     *
     * @param investmentExperience the investment experience to set
     */
    public void setInvestmentExperience(String investmentExperience) {
        this.investmentExperience = investmentExperience;
    }

    /**
     * Gets the date of birth.
     *
     * @return the date of birth
     */
    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * Sets the date of birth.
     *
     * @param dateOfBirth the date of birth to set
     */
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    /**
     * Gets the phone number.
     *
     * @return the phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the phone number.
     *
     * @param phoneNumber the phone number to set
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean getTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(Boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }
}

