package com.lemarketjames.trades;

/** Only the identity fields needed to disambiguate matching client names. */
public record ClientSearchResult(Integer clientId, String fullName) {}
