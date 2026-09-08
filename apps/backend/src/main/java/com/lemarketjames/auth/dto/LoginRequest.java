package com.lemarketjames.auth.dto;

/**
 * Data transfer object for user login requests.
 * Contains the username and password credentials required for authentication.
 */
public class LoginRequest {
    private String username;
    private String password;

    /**
     * Default constructor for deserialization.
     */
    public LoginRequest() {
    }

    /**
     * Constructs a LoginRequest with the provided credentials.
     *
     * @param username the username for login
     * @param password the password for login
     */
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
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
}
