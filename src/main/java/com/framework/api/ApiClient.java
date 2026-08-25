package com.framework.api;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.exceptions.ApiException;
import com.framework.reporting.ApiReportRecorder;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.utils.JsonUtils;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
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
 * <b>thread-local</b> (category 3), set by an application-specific auth
 * service's {@code login()} (e.g. {@code com.tests.application.services.AuthenticationService}
 * - application-specific, so it lives in {@code src/test}, not linkable from here) and
 * attached automatically to every subsequent call on that thread (unless
 * the call sets its own {@code Authorization} header) - the same "implicitly
 * available per-thread" ergonomics {@code WebDriverManager} gives Web
 * actions. As of Phase 8 the token itself lives in {@link ApiContext} (under
 * {@link ApiContext#ACCESS_TOKEN_KEY}) rather than a ThreadLocal private to
 * this class, so it participates in the same chaining/{@code ${{accessToken}}}
 * placeholder resolution as any other runtime variable - this class's public
 * API is unchanged.</p>
 *
 * <p><b>Reporting:</b> every call is logged to SLF4J (console/file) and, once complete,
 * recorded on the current test's {@link ApiReportRecorder} record for the API surface's own
 * self-contained HTML report - this class does not touch Extent or Allure at all (API tests are
 * deliberately excluded from both; see {@link ApiReportRecorder}'s javadoc for why Web/Mobile
 * are unaffected).</p>
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

        LOGGER.info("{} {}", request.method(), request.endpoint());
        Response response;
        try {
            response = spec.request(request.method(), request.endpoint());
        } catch (RuntimeException e) {
            throw new ApiException(
                    "API call failed: " + request.method() + " " + request.endpoint() + " - " + e.getMessage(), e);
        }
        logAndReport(request, headers, response);
        return new ApiResponse(response);
    }

    private static RestAssuredConfig restAssuredConfig() {
        int connectionTimeout = ConfigManager.getInt(ConfigKeys.API_CONNECTION_TIMEOUT, 10000);
        int socketTimeout = ConfigManager.getInt(ConfigKeys.API_SOCKET_TIMEOUT, 10000);
        return RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", connectionTimeout)
                .setParam("http.socket.timeout", socketTimeout));
    }

    /**
     * Logs the completed call to SLF4J (console/file) and records it, once, on the API report
     * (see class javadoc) - request and response together, matching {@code
     * ApiReportRecorder.logApiCall}'s one-event-per-call shape, so a test making several calls
     * (e.g. an E2E flow's create-event/book/verify/cleanup sequence) shows each as its own
     * ordered entry under that test's row, in the order they actually ran.
     */
    private static void logAndReport(ApiRequest request, Map<String, String> headers, Response response) {
        String url = SensitiveDataMasker.mask(resolvedUrl(request));
        int statusCode = response.getStatusCode();
        long durationMs = response.time();

        String maskedHeaders = SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(JsonUtils.toJson(headers)));
        String maskedRequestBody = request.body() != null
                ? SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(JsonUtils.toJson(request.body())))
                : null;
        String maskedResponseHeaders = SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(JsonUtils.toJson(headersToMap(response.headers()))));
        String maskedResponseBody = SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(response.getBody().asString()));

        LOGGER.info("-> {} ({} ms)", statusCode, durationMs);
        LOGGER.info("Request headers:\n{}", maskedHeaders);
        if (maskedRequestBody != null) {
            LOGGER.info("Request body:\n{}", maskedRequestBody);
        }
        LOGGER.info("Response headers:\n{}", maskedResponseHeaders);
        LOGGER.info("Response body:\n{}", maskedResponseBody);

        ApiReportRecorder.logApiCall(request.method().name(), request.endpoint(), url, statusCode, durationMs,
                maskedHeaders, maskedRequestBody, maskedResponseHeaders, maskedResponseBody);
    }

    private static Map<String, String> headersToMap(Headers headers) {
        Map<String, String> map = new LinkedHashMap<>();
        headers.forEach(header -> map.put(header.getName(), header.getValue()));
        return map;
    }

    /** {@code endpoint} with {@code {param}} placeholders substituted from {@code pathParams}, prefixed with the base URL and a {@code ?query=string} - the full URL shown in the report's expanded Request detail. */
    private static String resolvedUrl(ApiRequest request) {
        String path = request.endpoint();
        for (Map.Entry<String, Object> pathParam : request.pathParams().entrySet()) {
            path = path.replace("{" + pathParam.getKey() + "}", String.valueOf(pathParam.getValue()));
        }
        String url = ConfigManager.getApiBaseUrl() + path;
        if (!request.queryParams().isEmpty()) {
            String query = request.queryParams().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((a, b) -> a + "&" + b)
                    .orElse("");
            url = url + "?" + query;
        }
        return url;
    }
}
