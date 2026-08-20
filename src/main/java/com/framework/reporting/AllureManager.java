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
 */
public final class AllureManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureManager.class);

    private AllureManager() {
    }

    /** Attaches a PNG screenshot already saved to disk (e.g. by {@link com.framework.utils.ScreenshotUtils}). */
    public static void attachScreenshotFromPath(Path pngPath, String name) {
        try {
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(Files.readAllBytes(pngPath)), "png");
        } catch (IOException e) {
            LOGGER.warn("Failed to attach screenshot '{}' to Allure: {}", pngPath, e.getMessage());
        }
    }

    /** Attaches plain text (e.g. an already-masked API request/response body) as a {@code .txt} attachment. */
    public static void attachText(String name, String content) {
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
     * the same information for free via the Logback-to-Extent bridge ({@link
     * ExtentLoggingAppender} - {@code getCaseData}'s own {@code LOGGER.info(...)} call is enough),
     * since that bridge mirrors every {@code com.framework}/{@code com.tests} log line into the
     * report automatically; Allure has no equivalent bridge for arbitrary log lines, so this is
     * the one explicit call needed to give it the same visibility.</p>
     */
    public static void attachTestCaseMetadata(String testCaseId, String testCaseName) {
        try {
            Allure.parameter("Test Case ID", testCaseId);
            Allure.parameter("Test Case Name", testCaseName);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to attach test case metadata ('{}') to Allure: {}", testCaseId, e.getMessage());
        }
    }
}
