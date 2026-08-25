package com.framework.reporting;

import com.framework.reporting.ApiReportModel.ApiCallEvent;
import com.framework.reporting.ApiReportModel.AssertionEvent;
import com.framework.reporting.ApiReportModel.Outcome;
import com.framework.reporting.ApiReportModel.TestRecord;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects one {@link TestRecord} per API test method (thread-local - TestNG runs a given
 * test method start-to-finish on a single thread, even under {@code parallel="classes"} or
 * {@code parallel="methods"}) and accumulates every finished record into one suite-wide list
 * that {@link #flush()} hands to {@link ApiHtmlReportRenderer} to write once, at suite end.
 *
 * <p><b>Why a separate report from Extent/Allure:</b> the API surface gets its own
 * self-contained, dependency-free HTML report - a Newman/Postman-style dashboard grouped by
 * module, one row per test, full request/response detail, Expected/Actual assertions -
 * matching the reporting structure of the standalone {@code RestAssuredTestNG} framework
 * this was ported from. Web and Mobile are unaffected and keep enriching Extent/Allure
 * exactly as before (see {@link ExtentManager}/{@link AllureManager}); {@link
 * com.framework.api.ApiClient}, {@link com.framework.api.ApiResponse} and {@code
 * com.framework.utils.Verify} talk to this class instead when an API test is active (see
 * {@link #hasActiveTest()}).</p>
 *
 * <p>{@link com.framework.listeners.ApiTestReportListener} is the only place that starts/
 * finishes a test record or flushes the report - it decides which tests are "API tests" (the
 * {@code "api"} TestNG group, present on every method across every API test class - see that
 * listener's javadoc for why a group tag was chosen over a base-class check).</p>
 */
public final class ApiReportRecorder {

    private static final long SUITE_START_MILLIS = System.currentTimeMillis();
    private static final List<TestRecord> COMPLETED = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<TestRecord> CURRENT = new ThreadLocal<>();

    private ApiReportRecorder() {
    }

    public static void startTest(String name, String description, List<String> groups, String module) {
        CURRENT.set(new TestRecord(name, description, groups, module, System.currentTimeMillis()));
    }

    /** {@code true} once {@link #startTest} has run for the calling thread and before its matching {@link #finishTest} - lets {@code Verify} decide whether to report here or to Extent (see class javadoc). */
    public static boolean hasActiveTest() {
        return CURRENT.get() != null;
    }

    public static void logApiCall(String method, String endpoint, String url, int statusCode, long durationMs,
                                   String requestHeaders, String requestBody, String responseHeaders, String responseBody) {
        TestRecord test = CURRENT.get();
        if (test != null) {
            test.events.add(new ApiCallEvent(method, endpoint, url, statusCode, durationMs,
                    requestHeaders, requestBody, responseHeaders, responseBody));
        }
    }

    public static void logAssertion(String label, boolean passed, String expected, String actual, String detail) {
        TestRecord test = CURRENT.get();
        if (test != null) {
            test.events.add(new AssertionEvent(label, passed, expected, actual, detail));
        }
    }

    public static void finishTest(boolean passed, boolean skipped, String errorMessage) {
        TestRecord test = CURRENT.get();
        if (test == null) {
            return;
        }
        test.endMillis = System.currentTimeMillis();
        test.outcome = skipped ? Outcome.SKIP : (passed ? Outcome.PASS : Outcome.FAIL);
        test.errorMessage = errorMessage;
        COMPLETED.add(test);
        CURRENT.remove();
    }

    /**
     * Writes the whole suite's collected API results to disk as one HTML report - a no-op
     * when no API test ran this suite (a pure Web/Mobile run), so a plain {@code reports/api/}
     * file isn't written where nothing was ever collected. Safe to call more than once - each
     * call re-renders the current snapshot.
     */
    public static void flush() {
        if (COMPLETED.isEmpty()) {
            return;
        }
        ApiHtmlReportRenderer.render(SUITE_START_MILLIS, System.currentTimeMillis(), List.copyOf(COMPLETED));
    }
}
