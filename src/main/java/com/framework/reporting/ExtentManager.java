package com.framework.reporting;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.framework.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the single {@link ExtentReports} instance for the whole run and the current thread's
 * active {@link ExtentTest} node (requirement.md &sect;17).
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> {@link #EXTENT} is a
 * <b>thread-safe singleton</b> (category 2) - {@code ExtentReports.createTest}/{@code flush}
 * are safe to call concurrently, the documented and widely-used pattern for TestNG parallel
 * execution. {@link #CURRENT_TEST} is <b>thread-local</b> (category 3): each thread only ever
 * logs into its own current test node, never another thread's.</p>
 *
 * <p>Report node lifecycle is owned by {@link ExtentReportingListener}, scoped to each
 * {@code @Test} method's own invocation - see that class's javadoc for why
 * {@code @BeforeMethod}/{@code @AfterMethod} steps are not captured here (Allure's own
 * TestNG integration captures those natively instead).</p>
 */
public final class ExtentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtentManager.class);
    private static final Path REPORT_PATH = Path.of("reports", "extent", "index.html");

    private static final ExtentReports EXTENT = initExtent();
    private static final ThreadLocal<ExtentTest> CURRENT_TEST = new ThreadLocal<>();

    private ExtentManager() {
    }

    /** Starts a new report node for the calling thread and makes it the current test. */
    public static ExtentTest startTest(String name) {
        ExtentTest test = EXTENT.createTest(name);
        CURRENT_TEST.set(test);
        return test;
    }

    /** The calling thread's active report node, or {@code null} if none is active right now. */
    public static ExtentTest getTest() {
        return CURRENT_TEST.get();
    }

    /** Detaches the calling thread's current test node. Does not remove it from the report. */
    public static void endTest() {
        CURRENT_TEST.remove();
    }

    /** Writes the accumulated report to disk. Safe to call more than once (e.g. per {@code <test>} tag). */
    public static void flush() {
        EXTENT.flush();
    }

    private static ExtentReports initExtent() {
        try {
            Files.createDirectories(REPORT_PATH.getParent());
        } catch (IOException e) {
            LOGGER.warn("Failed to create Extent report directory '{}': {}", REPORT_PATH.getParent(), e.getMessage());
        }

        ExtentSparkReporter spark = new ExtentSparkReporter(REPORT_PATH.toString());
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setDocumentTitle("Web-Mobile-API Automation Report");
        spark.config().setReportName("Web-Mobile-API Automation Framework");

        ExtentReports extent = new ExtentReports();
        extent.attachReporter(spark);
        extent.setSystemInfo("Environment", ConfigManager.getEnvironment().name());
        LOGGER.info("Extent report will be written to '{}'.", REPORT_PATH);
        return extent;
    }
}
