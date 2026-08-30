package com.framework.listeners;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.reporting.ReportManager;
import io.qameta.allure.Allure;
import io.qameta.allure.SeverityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything Allure shows beyond a bare pass/fail that this framework derives automatically
 * from data it already has - zero test-author effort, matching every other reporting concern
 * in this codebase. No-ops entirely unless {@link ReportManager#isAllureEnabled()} (see that
 * class's javadoc for why).
 *
 * <ul>
 *     <li><b>Environment widget</b> ({@link #onStart}) - {@code allure-results/environment.properties},
 *     Allure's own convention for a per-run (not per-test) info panel: env, base URLs, browser,
 *     mobile platform/app path. Written once per suite, guarded by {@link #ENVIRONMENT_WRITTEN}
 *     since {@code ISuiteListener.onStart} can fire more than once in a multi-suite run.</li>
 *     <li><b>Feature/Story/Severity/Platform labels</b> ({@link #beforeInvocation}) - derived
 *     from the same Gherkin tags every scenario carries (via {@link CucumberScenarioSupport},
 *     see README's "Running tests" tag taxonomy table): Feature = the resource tag (Auth/Events/
 *     Bookings/System) if present, else the surface; Story = the bare Gherkin scenario name
 *     ({@link CucumberScenarioSupport#scenarioName} - deliberately not
 *     {@link CucumberScenarioSupport#displayName}'s feature-qualified version Extent/API use,
 *     since Allure already has its own Feature label right above, immediately to the left);
 *     Severity = {@code sanity} -&gt; blocker, {@code smoke}/{@code e2e} -&gt; critical, else
 *     normal; Platform = the raw surface tag (web/mobile/api), a plain custom label since Allure
 *     has no built-in concept matching it.</li>
 * </ul>
 *
 * <p><b>Not implemented here because they're already native, verified live against a real
 * {@code allure-results/*-result.json}:</b> retry grouping ({@code allure-testng} recognizes
 * {@code RetryAnalyzer}-driven reruns and links them under one {@code historyId} on its own) and
 * parallel/timeline data (every result already carries its own {@code start}/{@code stop} plus
 * host/thread identifiers Allure's report UI reads directly for the Timeline tab) - adding
 * either here would just be duplicating what {@code allure-testng} already does correctly.</p>
 */
public class AllureMetadataListener implements ISuiteListener, IInvokedMethodListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureMetadataListener.class);
    private static final AtomicBoolean ENVIRONMENT_WRITTEN = new AtomicBoolean(false);
    private static final List<String> SURFACES = List.of("web", "mobile", "api");
    private static final List<String> RESOURCES = List.of("auth", "events", "bookings", "system");

    @Override
    public void onStart(ISuite suite) {
        if (!ReportManager.isAllureEnabled() || !ENVIRONMENT_WRITTEN.compareAndSet(false, true)) {
            return;
        }
        writeEnvironmentProperties();
    }

    @Override
    public void onFinish(ISuite suite) {
        // Nothing to do at suite end - environment.properties is written once, at start.
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod() || !ReportManager.isAllureEnabled()) {
            return;
        }
        List<String> groups = CucumberScenarioSupport.groupsOrTags(method.getTestMethod(), testResult);
        if (groups.contains("api")) {
            // API tests get their own self-contained HTML report instead (see
            // ApiTestReportListener) and are deliberately excluded from Allure enrichment
            // entirely - allure-testng's own native bare pass/fail capture still runs
            // regardless (see ReportManager's javadoc), this only skips the added labels.
            return;
        }

        Allure.feature(featureFor(groups));
        // scenarioName() is the bare Gherkin scenario name for a Cucumber scenario (every test
        // today - Allure already has its own Feature label above, so no feature-qualified
        // prefix is wanted here, unlike Extent/API's displayName()), or this project's original
        // humanized-method-name fallback for a plain TestNG test.
        Allure.story(CucumberScenarioSupport.scenarioName(method.getTestMethod(), testResult));
        Allure.label("severity", severityFor(groups).value());
        surfaceOf(groups).ifPresent(surface -> Allure.label("platform", surface));
    }

    private static String featureFor(List<String> groups) {
        return RESOURCES.stream()
                .filter(groups::contains)
                .findFirst()
                .or(() -> surfaceOf(groups))
                .map(AllureMetadataListener::capitalize)
                .orElse("General");
    }

    private static SeverityLevel severityFor(List<String> groups) {
        if (groups.contains("sanity")) {
            return SeverityLevel.BLOCKER;
        }
        if (groups.contains("smoke") || groups.contains("e2e")) {
            return SeverityLevel.CRITICAL;
        }
        return SeverityLevel.NORMAL;
    }

    private static java.util.Optional<String> surfaceOf(List<String> groups) {
        return SURFACES.stream().filter(groups::contains).findFirst();
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Per-run facts, not per-test ones - {@code report.overwrite}/{@code testdata.format}-style
     * config values, deliberately not re-derived from a live driver (that varies per test and
     * belongs on the failure-time attachments {@link ScreenshotCaptureListener} adds instead -
     * see its own javadoc for exact browser/device version).
     */
    private static void writeEnvironmentProperties() {
        Properties properties = new Properties();
        properties.setProperty("Environment", ConfigManager.getEnvironment().name());
        properties.setProperty("Base URL", ConfigManager.getString(ConfigKeys.BASE_URL, ""));
        properties.setProperty("API Base URL", ConfigManager.getApiBaseUrl());
        properties.setProperty("Browser", ConfigManager.getBrowser());
        properties.setProperty("Headless", String.valueOf(ConfigManager.isHeadless()));
        properties.setProperty("Mobile Platform", ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM, ""));
        properties.setProperty("Mobile Device Provider", ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_PROVIDER, ""));

        Path path = Path.of("allure-results", "environment.properties");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Generated once per run by AllureMetadataListener - do not edit by hand.");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write Allure environment.properties: {}", e.getMessage());
        }
    }
}
