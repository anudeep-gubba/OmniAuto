package com.framework.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data collected during an API test run - no HTML, no third-party reporting library,
 * no rendering concerns. {@link ApiReportRecorder} fills this in as {@link
 * com.framework.api.ApiClient}/{@link com.framework.api.ApiResponse}/{@code Verify}/{@link
 * com.framework.listeners.ApiTestReportListener} report what happened; {@link
 * ApiHtmlReportRenderer} is the only class that turns it into a page, once, at suite end.
 *
 * <p>Deliberately separate from Extent/Allure (see {@link ApiReportRecorder}'s javadoc) -
 * this class knows nothing about either.</p>
 */
final class ApiReportModel {

    enum Outcome { PASS, FAIL, SKIP }

    sealed interface TestEvent permits ApiCallEvent, AssertionEvent {
    }

    /**
     * One HTTP call made during a test. {@code endpoint} is the relative path (e.g.
     * {@code /events/{id}}) shown on the collapsed row; {@code url} is the full
     * absolute URL shown inside the expanded Request detail. Headers/bodies are
     * already masked and pretty-printed by the time they get here - this class doesn't
     * re-check either.
     */
    record ApiCallEvent(String method, String endpoint, String url, int statusCode, long durationMs,
                         String requestHeaders, String requestBody, String responseHeaders, String responseBody) implements TestEvent {
    }

    /**
     * One assertion check ({@code Verify.assert*}/{@code ApiResponse#assertStatusCode}).
     * {@code expected}/{@code actual} are what the report shows as a plain "Expected / Actual"
     * pair instead of a technical assertion sentence; {@code detail} is optional extra context
     * (e.g. a response body dump on a status-code mismatch) shown only when present.
     */
    record AssertionEvent(String label, boolean passed, String expected, String actual, String detail) implements TestEvent {
    }

    static final class TestRecord {
        final String name;
        final String description;
        final List<String> groups;
        final String module;
        final long startMillis;
        final List<TestEvent> events = new ArrayList<>();
        long endMillis;
        Outcome outcome = Outcome.SKIP;
        String errorMessage;

        TestRecord(String name, String description, List<String> groups, String module, long startMillis) {
            this.name = name;
            this.description = description;
            this.groups = groups;
            this.module = module;
            this.startMillis = startMillis;
        }

        long durationMs() {
            return endMillis - startMillis;
        }
    }

    private ApiReportModel() {
    }
}
