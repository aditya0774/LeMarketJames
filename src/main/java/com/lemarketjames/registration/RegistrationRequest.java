package com.lemarketjames.registration;

public record RegistrationRequest(
        String firstName,
        String lastName,
        String address,
        String email,
        String phoneNumber,
        String password) {
}
