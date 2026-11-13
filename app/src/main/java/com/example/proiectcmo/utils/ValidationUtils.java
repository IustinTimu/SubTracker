package com.example.proiectcmo.utils;

import android.util.Patterns;

/**
 * Funcții de validare pentru datele introduse de utilizator.
 * Clasă utilitară: nu se instanțiază, se folosesc doar metodele statice.
 */
public final class ValidationUtils {

    /** Lungimea minimă a parolei (Firebase cere cel puțin 6 caractere). */
    public static final int MIN_PASSWORD_LENGTH = 6;

    private ValidationUtils() {
    }

    /** @return true dacă textul nu este null și nu conține doar spații. */
    public static boolean isNotBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /** @return true dacă adresa are un format valid de email (ex. nume@domeniu.ro). */
    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    /** @return true dacă parola are cel puțin {@link #MIN_PASSWORD_LENGTH} caractere. */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }
}
