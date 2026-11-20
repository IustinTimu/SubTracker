package com.example.proiectcmo.utils;

import android.util.Patterns;

public final class ValidationUtils {
    public static final int MIN_PASSWORD_LENGTH = 6;

    private ValidationUtils() {
    }

    public static boolean isNotBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }
}
