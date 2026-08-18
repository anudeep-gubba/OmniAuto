package com.framework.api;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.exceptions.ApiException;
import com.framework.reporting.AllureManager;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.utils.JsonUtils;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Executes an {@link ApiRequest} against {@code api.base.url} and returns an
 * {@link ApiResponse} - the one place REST Assured is actually called from
 * (RULE 12: no raw REST Assured scattered through test/service code).
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> every
 * {@link #execute(ApiRequest)} call builds a brand-new REST Assured request
 * specification - nothing shared across threads there. The one piece of
 * state this class owns, the current bearer token, is deliberately
 * <b>thread-local</b> (category 3), set by
 * {@link com.framework.api.services.AuthenticationService#login} and
 * attached automatically to every subsequent call on that thread (unless
 * the call sets its own {@code Authorization} header) - the same "implicitly
 * available per-thread" ergonomics {@code WebDriverManager} gives Web
 * actions. As of Phase 8 the token itself lives in {@link ApiContext} (under
 * {@link ApiContext#ACCESS_TOKEN_KEY}) rather than a ThreadLocal private to
 * this class, so it participates in the same chaining/{@code ${{accessToken}}}
 * placeholder resolution as any other runtime variable - this class's public
 * API is unchanged.</p>
 */
public final class ApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiClient.class);

    private ApiClient() {
    }

    public static void setAuthToken(String token) {
        ApiContext.set(ApiContext.ACCESS_TOKEN_KEY, token);
    }

    public static void clearAuthToken() {
        ApiContext.remove(ApiContext.ACCESS_TOKEN_KEY);
    }

    public static boolean hasAuthToken() {
        return ApiContext.has(ApiContext.ACCESS_TOKEN_KEY);
    }

    public static ApiResponse execute(ApiRequest request) {
        Map<String, String> headers = ApiHeaders.build(request.headers(), ApiContext.getOptional(ApiContext.ACCESS_TOKEN_KEY).orElse(null));

        RequestSpecification spec = RestAssured.given()
                .config(restAssuredConfig())
                .baseUri(ConfigManager.getApiBaseUrl())
                .headers(headers);
        request.queryParams().forEach(spec::queryParam);
        request.pathParams().forEach(spec::pathParam);
        if (request.body() != null) {
            spec.body(request.body());
        }

        logRequest(request, headers);
        Response response;
        try {
            response = spec.request(request.method(), request.endpoint());
        } catch (RuntimeException e) {
            throw new ApiException(
                    "API call failed: " + request.method() + " " + request.endpoint() + " - " + e.getMessage(), e);
        }
        logResponse(response);
        return new ApiResponse(response);
    }

    private static RestAssuredConfig restAssuredConfig() {
        int connectionTimeout = ConfigManager.getInt(ConfigKeys.API_CONNECTION_TIMEOUT, 10000);
        int socketTimeout = ConfigManager.getInt(ConfigKeys.API_SOCKET_TIMEOUT, 10000);
        return RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", connectionTimeout)
                .setParam("http.socket.timeout", socketTimeout));
    }

    private static void logRequest(ApiRequest request, Map<String, String> headers) {
        LOGGER.info("{} {}", request.method(), request.endpoint());
        if (!request.pathParams().isEmpty()) {
            LOGGER.info("Path params: {}", request.pathParams());
        }
        if (!request.queryParams().isEmpty()) {
            LOGGER.info("Query params: {}", request.queryParams());
        }
        LOGGER.info("Request headers: {}", SensitiveDataMasker.mask(headers.toString()));

        String maskedBody = request.body() != null ? SensitiveDataMasker.mask(JsonUtils.toJson(request.body())) : null;
        if (maskedBody != null) {
            LOGGER.info("Request body: {}", maskedBody);
        }
        // Allure attachment (requirement.md section 17): reuses the same already-masked string
        // logged above rather than re-masking, so there is exactly one place that decides what's safe to show.
        AllureManager.attachText(request.method() + " " + request.endpoint() + " - request",
                maskedBody != null ? maskedBody : "(no body)");
    }

    private static void logResponse(Response response) {
        LOGGER.info("Response status: {}", response.getStatusCode());
        String maskedBody = SensitiveDataMasker.mask(response.getBody().asString());
        LOGGER.info("Response body: {}", maskedBody);
        AllureManager.attachText("Response " + response.getStatusCode(), maskedBody);
    }
}
