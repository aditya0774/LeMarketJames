package com.lemarketjames.profile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Self-service client + account profile — a client can only ever fetch their own (see ProfileService). */
public class ProfileDto {

    private final String username;
    private final String fullName;
    private final String email;
    private final String phone;
    private final Integer accountId;
    private final BigDecimal cashBalance;
    private final String currency;
    private final boolean tradingEnabled;
    private final LocalDate openedDate;

    public ProfileDto(String username, String fullName, String email, String phone, Integer accountId,
                       BigDecimal cashBalance, String currency, boolean tradingEnabled, LocalDate openedDate) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.accountId = accountId;
        this.cashBalance = cashBalance;
        this.currency = currency;
        this.tradingEnabled = tradingEnabled;
        this.openedDate = openedDate;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isTradingEnabled() {
        return tradingEnabled;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }
}
