package com.gpay.auth_service.security;

import java.util.regex.Pattern;

/* Centralizes the password complexity policy shared by registration and admin bootstrap. */
public final class PasswordPolicy {

    /** Minimum 8 characters with at least one uppercase, lowercase, digit, and special character. */
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private PasswordPolicy() {}

    /**
     * Reports whether the password satisfies the complexity policy.
     *
     * @param password plain-text candidate password
     * @return true when the password is non-null and meets all complexity rules
     */
    public static boolean isCompliant(String password) {
        return password != null && COMPILED.matcher(password).matches();
    }
}
