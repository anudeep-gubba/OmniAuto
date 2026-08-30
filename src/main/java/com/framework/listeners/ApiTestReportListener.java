package com.framework.listeners;

import com.framework.reporting.ApiReportRecorder;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wires TestNG's test lifecycle to {@link ApiReportRecorder} for API tests only, so the API
 * surface gets its own self-contained HTML report (module grouping, every request/response
 * made, every assertion, failure details) - matching the reporting structure of the standalone
 * {@code RestAssuredTestNG} framework this was ported from - while Web/Mobile tests are
 * completely unaffected and keep enriching Extent/Allure exactly as before (see
 * {@link ExtentReportingListener}/{@link AllureMetadataListener}).
 *
 * <p>{@link com.framework.api.ApiClient}/{@link com.framework.api.ApiResponse}/{@code
 * com.framework.utils.Verify} log each request/response and each assertion straight to the
 * current test record as they happen, so the report reads in the same order the test actually
 * ran: call, then the checks made against it. This listener only owns the record's start/end
 * and the module a test belongs to.</p>
 *
 * <p><b>"Is this an API test?" is decided by the {@code @api} Gherkin tag</b> (via
 * {@link CucumberScenarioSupport#groupsOrTags}, itself the {@code "api"} TestNG group for a
 * plain TestNG test), not by the step-definition class extending a common API base: every
 * {@code features/api/**} scenario carries it, and this needs no compile-time dependency from
 * this class (which lives in {@code src/main/java}, compiled before {@code src/test/java}) on
 * any test-scoped step-definition/context class.</p>
 *
 * <p>Self-registers via {@code META-INF/services/org.testng.ITestNGListener} - no
 * {@code testng.xml} or {@code @Listeners} annotation needed anywhere.</p>
 */
public class ApiTestReportListener implements ITestListener, ISuiteListener {

    private static final String API_GROUP = "api";

    /** Domain tag -&gt; display name for module grouping. A tag not listed here falls back to a title-cased version of itself (see {@link #moduleFor}), so a new domain tag doesn't need a code change to show up correctly grouped - just less prettily named until added here. */
    private static final Map<String, String> MODULE_NAMES = Map.of(
            "auth", "Authentication",
            "bookings", "Bookings",
            "events", "Events",
            "system", "Health & Config",
            "e2e", "End-to-End"
    );

    /** Never a module name on its own - a run-selector or coverage tag every API test tends to carry alongside its real domain tag(s). */
    private static final Set<String> NON_MODULE_GROUPS = Set.of(API_GROUP, "smoke", "regression", "sanity", "positive", "negative");

    @Override
    public void onTestStart(ITestResult result) {
        if (!isApiTest(result)) {
            return;
        }
        List<String> groups = CucumberScenarioSupport.groupsOrTags(result.getMethod(), result);
        // For a Cucumber scenario, result.getMethod().getDescription() is cucumber-testng's own
        // generic "Runs Cucumber Scenarios" @Test(description=...) on runScenario() - not
        // anything scenario-specific - so it's blanked out here rather than shown on every row.
        String description = CucumberScenarioSupport.isCucumberScenario(result.getMethod())
                ? "" : result.getMethod().getDescription();
        String name = CucumberScenarioSupport.displayName(result.getMethod(), result);
        ApiReportRecorder.startTest(name, description == null ? "" : description, groups, moduleFor(groups));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (isApiTest(result)) {
            ApiReportRecorder.finishTest(true, false, null);
        }
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (isApiTest(result)) {
            Throwable t = result.getThrowable();
            ApiReportRecorder.finishTest(false, false, t == null ? "Test failed" : String.valueOf(t.getMessage()));
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (!isApiTest(result)) {
            return;
        }
        // A test can be skipped before onTestStart ever ran for it (e.g. a failed
        // @BeforeClass/@BeforeMethod skips every @Test in the class without TestNG ever calling
        // onTestStart) - start a record now so it still shows up on the report.
        if (!ApiReportRecorder.hasActiveTest()) {
            onTestStart(result);
        }
        Throwable t = result.getThrowable();
        ApiReportRecorder.finishTest(false, true, t == null ? "Skipped" : t.getMessage());
    }

    @Override
    public void onFinish(ITestContext context) {
        // no-op: report is flushed once per suite in onFinish(ISuite), not per <test>
    }

    @Override
    public void onFinish(ISuite suite) {
        ApiReportRecorder.flush();
    }

    private static boolean isApiTest(ITestResult result) {
        return CucumberScenarioSupport.groupsOrTags(result.getMethod(), result).contains(API_GROUP);
    }

    private static String moduleFor(List<String> groups) {
        for (String group : groups) {
            String mapped = MODULE_NAMES.get(group);
            if (mapped != null) {
                return mapped;
            }
        }
        for (String group : groups) {
            if (!NON_MODULE_GROUPS.contains(group)) {
                return Character.toUpperCase(group.charAt(0)) + group.substring(1);
            }
        }
        return "Other";
    }
}
