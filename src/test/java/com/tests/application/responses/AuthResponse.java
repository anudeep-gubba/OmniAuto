package com.tests.application.responses;

/** Body of {@code POST /auth/register} (201) and {@code POST /auth/login} (200). */
public record AuthResponse(boolean success, String token, AuthUser user) {

    public record AuthUser(int id, String email) {
    }
}
