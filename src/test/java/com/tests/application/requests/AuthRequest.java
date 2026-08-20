package com.tests.application.requests;

/** Body shared by {@code POST /auth/register} and {@code POST /auth/login} (identical shape on eventhub's API). */
public record AuthRequest(String email, String password) {
}
