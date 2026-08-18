package com.framework.api.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.framework.api.requests.AuthRequest;
import com.framework.api.responses.AuthResponse;
import com.framework.api.responses.MeResponse;
import com.framework.exceptions.ApiAuthenticationException;
import com.framework.secrets.SensitiveDataMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps eventhub's {@code /auth/*} endpoints (requirement.md &sect;9/&sect;10:
 * "userService.createUser(request); rather than directly implementing REST
 * Assured logic").
 *
 * <p>Returns the already-parsed {@link AuthResponse}, not a raw
 * {@link ApiResponse} like the other services: {@code register}/{@code login}
 * must parse the body internally anyway, to extract and store the token via
 * {@link ApiClient#setAuthToken(String)}, so handing the caller the same
 * parsed result avoids a redundant re-parse.</p>
 *
 * <p><b>Found in practice, not assumed</b> (Phase 10): every log/exception message here
 * that embeds {@code email} routes it through {@link SensitiveDataMasker#mask(String)},
 * unlike an earlier version that logged it raw. A live capture of this class's actual log
 * output caught the gap directly - eventhub credentials are stored as secrets
 * ({@code EVENTHUB_EMAIL}/{@code EVENTHUB_PASSWORD}, see {@code eventhub-test-targets}
 * project notes), so the email {@link com.framework.secrets.SecretManager} resolves is a
 * registered secret value like any other and must be masked everywhere, not just in
 * {@link ApiClient}'s own request/response logging. {@link SensitiveDataMasker#mask(String)}
 * is a safe no-op for emails that were never registered as a secret (e.g. a freshly
 * generated test-registration address), so this does not change behavior for those.</p>
 */
public final class AuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    public AuthResponse register(String email, String password) {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register").body(new AuthRequest(email, password)));
        if (response.statusCode() != 201) {
            throw new ApiAuthenticationException("Registration failed for '" + SensitiveDataMasker.mask(email)
                    + "': HTTP " + response.statusCode() + " - " + SensitiveDataMasker.mask(response.body()));
        }
        AuthResponse auth = response.as(AuthResponse.class);
        ApiClient.setAuthToken(auth.token());
        LOGGER.info("Registered and authenticated as '{}'.", SensitiveDataMasker.mask(email));
        return auth;
    }

    public AuthResponse login(String email, String password) {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/login").body(new AuthRequest(email, password)));
        if (response.statusCode() != 200) {
            throw new ApiAuthenticationException("Login failed for '" + SensitiveDataMasker.mask(email)
                    + "': HTTP " + response.statusCode() + " - " + SensitiveDataMasker.mask(response.body()));
        }
        AuthResponse auth = response.as(AuthResponse.class);
        ApiClient.setAuthToken(auth.token());
        LOGGER.info("Authenticated as '{}'.", SensitiveDataMasker.mask(email));
        return auth;
    }

    public MeResponse me() {
        if (!ApiClient.hasAuthToken()) {
            throw new ApiAuthenticationException("No auth token set for this thread - call login()/register() first.");
        }
        ApiResponse response = ApiClient.execute(ApiRequest.get("/auth/me"));
        if (response.statusCode() != 200) {
            throw new ApiAuthenticationException("Token validation failed: HTTP " + response.statusCode()
                    + " - " + SensitiveDataMasker.mask(response.body()));
        }
        return response.as(MeResponse.class);
    }

    /** Clears this thread's stored token; subsequent calls go out unauthenticated until the next login()/register(). */
    public void logout() {
        ApiClient.clearAuthToken();
    }
}
