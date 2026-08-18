package com.framework.api.responses;

/** Body of {@code GET /auth/me} - the decoded JWT identity for the current token. */
public record MeResponse(boolean success, MeUser user) {

    public record MeUser(int userId, String email, long iat, long exp) {
    }
}
