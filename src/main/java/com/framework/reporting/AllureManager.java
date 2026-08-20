package com.framework.reporting;

import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thin wrapper around {@code io.qameta.allure.Allure}'s static attachment API
 * (requirement.md &sect;17). Allure's own {@code allure-testng} integration - already on
 * the classpath and auto-registered via its own {@code META-INF/services} entry, with
 * AspectJ weaving already wired into Surefire's {@code argLine} (see {@code pom.xml}) -
 * handles test results, {@code @BeforeMethod}/{@code @AfterMethod} sections, groups, and
 * retry grouping automatically; this class only adds the couple of attachment operations
 * the framework needs beyond that, so every call site shares one consistent implementation
 * (RULE 5) instead of scattering raw {@code Allure.addAttachment(...)} calls with
 * inconsistent naming/MIME types.
 *
 * <p><b>Callers are responsible for masking</b> - same as every other logging/reporting call
 * site in this framework (see the {@code masking-is-opt-in-per-call-site} project note): pass
 * already-{@link com.framework.secrets.SensitiveDataMasker#mask(String)}-ed text, this class
 * does not mask on your behalf.</p>
 *
 * <p>Best-effort like {@link com.framework.utils.ScreenshotUtils}: an attachment failure logs
 * a warning rather than failing an otherwise-passing test over diagnostic infrastructure.</p>
 *
 * <p><b>Every method here is a no-op unless {@link ReportManager#isAllureEnabled()}</b> - the
 * formatting/masking work behind a call site is real cost even when the attachment itself would
 * be cheap, so this skips it outright rather than doing it and handing Allure something nobody
 * asked for (see {@link ReportManager}'s own javadoc). {@code allure-testng}'s own native
 * pass/fail/{@code @Before}/{@code @After} capture is unaffected either way - this class only
 * ever added to that, never replaced it.</p>
 */
public final class AllureManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureManager.class);

    private AllureManager() {
    }

    /** Attaches a PNG screenshot already saved to disk (e.g. by {@link com.framework.utils.ScreenshotUtils}). No-op if Allure is disabled. */
    public static void attachScreenshotFromPath(Path pngPath, String name) {
        if (!ReportManager.isAllureEnabled()) {
            return;
        }
        try {
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(Files.readAllBytes(pngPath)), "png");
        } catch (IOException e) {
            LOGGER.warn("Failed to attach screenshot '{}' to Allure: {}", pngPath, e.getMessage());
        }
    }

    /** Attaches plain text (e.g. an already-masked API request/response body) as a {@code .txt} attachment. No-op if Allure is disabled. */
    public static void attachText(String name, String content) {
        if (!ReportManager.isAllureEnabled()) {
            return;
        }
        try {
            Allure.addAttachment(name, "text/plain",
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "txt");
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to attach '{}' to Allure: {}", name, e.getMessage());
        }
    }

    /**
     * Surfaces a test-case-data row's {@code testCaseId}/{@code testCaseName} (see {@link
     * com.framework.testdata.TestCaseMetadata}) as parameters on the currently-running Allure
     * test result - shown in that test's own "Parameters" table, and filterable/sortable across
     * a whole run, so a report reader can immediately see (or search for) which named test case
     * a given result was driven by without opening the test data file.
     *
     * <p>Called once, centrally, from {@link com.framework.testdata.TestDataManager#getCaseData}
     * - no test author ever adds this themselves (requirement.md &sect;18). Extent already gets
     * the same information for free via the Logback-to-Extent bridge ({@code
     * getCaseData}'s own {@code LOGGER.info(...)} call is enough); {@link AllureLoggingAppender}
     * gives Allure an equivalent bridge for ordinary business-narrative log lines, but a
     * structured "Parameters" table entry (filterable/sortable across a whole run) is a step
     * beyond what mirroring a log line as a step gives you, so this stays its own explicit call.</p>
     */
    public static void attachTestCaseMetadata(String testCaseId, String testCaseName) {
        attachParameter("Test Case ID", testCaseId);
        attachParameter("Test Case Name", testCaseName);
    }

    /**
     * Adds {@code name}/{@code value} to the currently-running Allure test result's own
     * "Parameters" table - for a single structured fact (current URL, browser version, device
     * name, response time, ...) where a full text/screenshot attachment would be overkill, and
     * a plain log line (already masked, already reaching Allure via {@link
     * AllureLoggingAppender} for the business-narrative loggers) doesn't give the
     * filterable/sortable table Allure's own UI shows parameters in. No-op if Allure is disabled.
     */
    public static void attachParameter(String name, String value) {
        if (!ReportManager.isAllureEnabled()) {
            return;
        }
        try {
            Allure.parameter(name, value);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to attach parameter '{}' to Allure: {}", name, e.getMessage());
        }
    }
}
