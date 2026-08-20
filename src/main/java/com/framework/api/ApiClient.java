package com.framework.api;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.exceptions.ApiException;
import com.framework.reporting.AllureManager;
import com.framework.reporting.ExtentManager;
import com.framework.reporting.ReportManager;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.utils.JsonUtils;
import io.qameta.allure.Allure;
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

    /**
     * Wrapped in an {@link Allure#step}, when Allure is enabled ({@link
     * ReportManager#isAllureEnabled()}), so a test making several calls (e.g.
     * {@code EventBookingChainingTest}'s create-event/book/verify/cleanup sequence) shows each
     * call as its own ordered, collapsible step in the Allure report - with that call's
     * request/response attachments nested under it - instead of every call's attachments
     * dumped as one flat, unordered list directly on the test (indistinguishable from each
     * other when two calls share an endpoint, and with no visual grouping of which
     * request belongs with which response at all). Skipped when Allure is disabled - an empty
     * step with nothing nested under it (every attachment call site already no-ops itself, see
     * {@link AllureManager}) is pure overhead at that point.
     */
    public static ApiResponse execute(ApiRequest request) {
        if (!ReportManager.isAllureEnabled()) {
            return doExecute(request);
        }
        String stepName = request.method() + " " + request.endpoint();
        return Allure.step(stepName, () -> doExecute(request));
    }

    private static ApiResponse doExecute(ApiRequest request) {
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

    /**
     * Logs to two different destinations deliberately, not one generic call each: SLF4J
     * ({@code LOGGER}) for CONSOLE/FILE, where a single-line summary is what a reader scanning
     * a log file actually wants, and {@link ExtentManager} directly for the report, where the
     * full masked headers/body render properly (see {@link ExtentManager#logCodeBlock}'s
     * javadoc for why a generic Logback mirror can't do that job here). {@code com.framework.api}
     * is deliberately not wired to the {@code EXTENT} appender in {@code logback.xml} for this
     * reason - relying on both paths for the same content would either duplicate it or squash
     * the Extent copy back down to an unreadable single line.
     */
    /**
     * {@code AllureManager.attachParameter} calls in here and in {@link #logResponse} are a
     * known, accepted trade-off for a test making more than one API call (the E2E flow classes -
     * the ~60 single-call tests elsewhere are unaffected): {@code Allure.parameter(...)} is a
     * test-result-level table, not scoped to the current step, so each call's HTTP
     * Method/Request URL/Response Time simply overwrites the previous call's entry there rather
     * than accumulating per-call. The request/response body/header <em>attachments</em> (via
     * {@code attachText}) don't have this problem - {@code Allure.addAttachment} correctly nests
     * under whichever step is active - so every call's full detail is still fully recoverable
     * from its own step, just not summarized in the flat parameters table for calls before the
     * last one. Fixing this needs the step-context-aware {@code Allure.step(String,
     * ThrowableContextRunnableVoid<StepContext>)} overload instead of the plain one {@link
     * #execute} uses; not done here given how few tests it actually affects.
     */
    private static void logRequest(ApiRequest request, Map<String, String> headers) {
        String stepLabel = request.method() + " " + request.endpoint();
        LOGGER.info(stepLabel);
        ExtentManager.logInfo(stepLabel);
        AllureManager.attachParameter("HTTP Method", request.method().name());
        AllureManager.attachParameter("Request URL", SensitiveDataMasker.mask(resolvedUrl(request)));

        if (!request.pathParams().isEmpty()) {
            LOGGER.info("Path params: {}", request.pathParams());
            AllureManager.attachText("Path Params", SensitiveDataMasker.mask(JsonUtils.toJson(request.pathParams())));
        }
        if (!request.queryParams().isEmpty()) {
            LOGGER.info("Query params: {}", request.queryParams());
            AllureManager.attachText("Query Params", SensitiveDataMasker.mask(JsonUtils.toJson(request.queryParams())));
        }
        String maskedHeaders = SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(JsonUtils.toJson(headers)));
        LOGGER.info("Request headers:\n{}", maskedHeaders);
        ExtentManager.logCodeBlock("Request Headers", maskedHeaders);
        AllureManager.attachText("Request Headers", maskedHeaders);

        String maskedBody = request.body() != null
                ? SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(JsonUtils.toJson(request.body())))
                : null;
        if (maskedBody != null) {
            LOGGER.info("Request body:\n{}", maskedBody);
            ExtentManager.logCodeBlock("Request Body", maskedBody);
        }
        // Allure attachment (requirement.md section 17): reuses the same already-masked, already
        // pretty-printed string logged above rather than re-masking/re-formatting, so there is
        // exactly one place that decides both what's safe to show and how it reads.
        AllureManager.attachText("Request Body", maskedBody != null ? maskedBody : "(no body)");
    }

    /** {@code endpoint} with {@code {param}} placeholders substituted from {@code pathParams}, prefixed with the base URL and a {@code ?query=string} - display only, purely for the "Request URL" attachment. */
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

    private static void logResponse(Response response) {
        int statusCode = response.getStatusCode();
        String statusLine = "Response status: " + statusCode;
        LOGGER.info(statusLine);
        ExtentManager.logStatusLine(statusLine, statusCode);
        AllureManager.attachParameter("Response Status Code", String.valueOf(statusCode));

        long responseTimeMs = response.time();
        String timeLine = "Response time: " + responseTimeMs + " ms";
        LOGGER.info(timeLine);
        ExtentManager.logInfo(timeLine);
        AllureManager.attachParameter("Response Time (ms)", String.valueOf(responseTimeMs));

        String maskedResponseHeaders = SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(JsonUtils.toJson(headersToMap(response.headers()))));
        LOGGER.info("Response headers:\n{}", maskedResponseHeaders);
        ExtentManager.logCodeBlock("Response Headers", maskedResponseHeaders);
        AllureManager.attachText("Response Headers", maskedResponseHeaders);

        String maskedBody = SensitiveDataMasker.mask(JsonUtils.prettyPrintJson(response.getBody().asString()));
        LOGGER.info("Response body:\n{}", maskedBody);
        ExtentManager.logCodeBlock("Response Body", maskedBody, statusCode);
        AllureManager.attachText("Response Body (" + statusCode + ")", maskedBody);
    }

    private static Map<String, String> headersToMap(Headers headers) {
        Map<String, String> map = new LinkedHashMap<>();
        headers.forEach(header -> map.put(header.getName(), header.getValue()));
        return map;
    }
}
