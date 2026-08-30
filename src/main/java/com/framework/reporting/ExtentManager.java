package com.framework.reporting;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.Markup;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Owns the single {@link ExtentReports} instance for the whole run and the current thread's
 * active {@link ExtentTest} node (requirement.md &sect;17).
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> {@link #EXTENT} is a
 * <b>thread-safe singleton</b> (category 2) - {@code ExtentReports.createTest}/{@code flush}
 * are safe to call concurrently, the documented and widely-used pattern for TestNG parallel
 * execution. {@link #NODE_STACK} is <b>thread-local</b> (category 3): each thread only ever
 * logs into its own current node, never another thread's.</p>
 *
 * <p>Report node lifecycle is owned by {@link ExtentReportingListener}, scoped to each
 * {@code @Test} method's own invocation - see that class's javadoc for why
 * {@code @BeforeMethod}/{@code @AfterMethod} steps are not captured here (Allure's own
 * TestNG integration captures those natively instead).</p>
 *
 * <p><b>Gherkin/BDD step nodes:</b> {@link #getTest()} returns whichever node is "current" for
 * the calling thread right now - the scenario-level root node started by {@link #startTest}, or,
 * while a Gherkin step is executing, the child step node pushed by {@link #startStep}. This is
 * what makes every existing call site ({@code com.framework.utils.Verify}, {@link
 * ExtentLoggingAppender}, {@code ApiClient}'s request/response logging - all written before this
 * class had any notion of a "step") automatically nest its logging under the right
 * Given/When/Then node with zero changes of its own: they were always calling {@code getTest()}
 * and logging into whatever it returned - only what it returns changed. {@link #NODE_STACK} is a
 * stack, not a single reference, so a step's own node can be pushed on top of the scenario root
 * and popped back off again once that step finishes ({@code
 * com.framework.listeners.CucumberExtentStepListener} is what calls {@link #startStep}/{@link
 * #endStep}, driven by Cucumber's own {@code TestStepStarted}/{@code TestStepFinished} events) -
 * Cucumber steps run strictly one at a time within a single scenario/thread, so there is never
 * more than one step node active per thread, but the stack shape stays honest even so: {@link
 * #endStep} refuses to pop the root scenario node itself (leaves it in place) if called with no
 * step actually pushed, rather than silently detaching the scenario's own node.</p>
 *
 * <p><b>{@link ReportManager#isExtentEnabled()} gates everything here</b> - when {@code false}
 * ({@link com.framework.constants.ConfigKeys#REPORT_TYPES} excludes {@code "extent"}), {@link
 * #EXTENT} is never created (no Spark reporter, no report file written at all - not an
 * empty/near-empty one) and {@link #startTest} is a no-op returning {@code null}. Every other
 * method here already null-checks {@link #getTest()} before doing anything (the same guard that
 * makes them safe outside a {@code @Test} invocation window in the first place), so disabling
 * Extent needed no changes anywhere else in this class - the existing null-safety is what makes
 * this cheap.</p>
 */
public final class ExtentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtentManager.class);
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final Path REPORT_PATH = resolveReportPath();

    private static final ExtentReports EXTENT = ReportManager.isExtentEnabled() ? initExtent() : null;
    private static final ThreadLocal<Deque<ExtentTest>> NODE_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ExtentManager() {
    }

    /** Starts a new scenario-level report node for the calling thread and makes it the current node - a no-op returning {@code null} if Extent is disabled (see class javadoc). */
    public static ExtentTest startTest(String name) {
        if (EXTENT == null) {
            return null;
        }
        ExtentTest test = EXTENT.createTest(name);
        Deque<ExtentTest> stack = NODE_STACK.get();
        stack.clear();
        stack.push(test);
        return test;
    }

    /** The calling thread's current node - the scenario root, or a Gherkin step's own node while one is active (see class javadoc) - or {@code null} if none is active right now (or Extent is disabled). */
    public static ExtentTest getTest() {
        return NODE_STACK.get().peek();
    }

    /** Detaches the calling thread's current node (and any step node still on top of it). Does not remove anything from the report. */
    public static void endTest() {
        NODE_STACK.remove();
    }

    /**
     * Starts a Gherkin step node ({@code keyword} - "Given"/"When"/"Then"/"And"/"But"/"*") as a
     * child of the calling thread's current node, and makes it the new current node so every log
     * line/assertion made while this step runs nests under it instead of the bare scenario root
     * - see class javadoc. No-op returning {@code null} if Extent is disabled or no scenario
     * node is active yet (e.g. a step somehow running outside {@link ExtentReportingListener}'s
     * node lifecycle).
     *
     * <p><b>{@code keyword} is prepended as plain text, not passed through Extent's own {@link
     * com.aventstack.extentreports.GherkinKeyword}.</b> Audit finding, verified live with a
     * standalone {@code ExtentSparkReporter} run outside this codebase entirely: a node created
     * via {@code createNode(new GherkinKeyword("Given"), text)} renders byte-for-byte identical
     * HTML to a plain {@code createNode(text)} node in ExtentReports 5.1.2's Spark theme - no
     * icon, no keyword prefix, no distinguishing class or attribute anywhere in the output. The
     * class exists and resolves without error; the Spark reporter's own HTML template simply
     * never reads it. Prepending the keyword ourselves is the only way this project found to
     * actually get "Given ..."/"When ..."/"Then ..." to show up in the rendered report.</p>
     */
    public static ExtentTest startStep(String keyword, String text) {
        ExtentTest parent = getTest();
        if (parent == null) {
            return null;
        }
        ExtentTest step = parent.createNode(keyword + " " + text);
        NODE_STACK.get().push(step);
        return step;
    }

    /**
     * Ends the calling thread's current Gherkin step node, popping back to its parent scenario
     * node. Refuses to pop the scenario root itself (a no-op instead) if called with no step
     * actually active - see class javadoc.
     */
    public static void endStep() {
        Deque<ExtentTest> stack = NODE_STACK.get();
        if (stack.size() > 1) {
            stack.pop();
        }
    }

    /** Writes the accumulated report to disk. Safe to call more than once (e.g. per {@code <test>} tag), and a no-op if Extent is disabled. */
    public static void flush() {
        if (EXTENT != null) {
            EXTENT.flush();
        }
    }

    /** Logs a plain info line to the calling thread's current node, if any. No-op otherwise (see {@link ExtentLoggingAppender}'s own javadoc on that being the expected common case). */
    public static void logInfo(String message) {
        ExtentTest test = getTest();
        if (test != null) {
            test.info(message);
        }
    }

    /**
     * Logs {@code status}/{@code message} - the pass/fail counterpart to {@link #logInfo(String)},
     * for a single named assertion (see {@code com.framework.utils.Verify}/{@code
     * ApiResponse#assertStatusCode}). {@code status}'s own badge in the Status column (Extent's
     * default Pass/Fail coloring) already flags row-by-row severity while scanning down a
     * report, but doesn't help when several assertion rows are visible on screen together and
     * only one failed - the message itself is colored too (green for {@code PASS}, red
     * otherwise), the same treatment {@link #logStatusLine} already gives an HTTP status line,
     * so a failing check is visually distinct from the passing ones around it, not just
     * distinguishable by a small badge off to the side. No-op if no node is active.
     */
    public static void logAssertion(Status status, String message) {
        ExtentTest test = getTest();
        if (test != null) {
            test.log(status, coloredLabel(message, status == Status.PASS ? ExtentColor.GREEN : ExtentColor.RED));
        }
    }

    /**
     * Logs {@code throwable}'s full stack trace as its own red-badged FAIL row, whitespace
     * preserved the same way {@link #logCodeBlock} renders a body - a one-line assertion
     * message says <em>that</em> something failed, not where in the call chain or why beyond
     * the message text itself. Extent's own {@code ExtentTest#fail(Throwable)} already renders
     * this into a {@code <textarea readonly>} (verified live), so this is a thin, named wrapper
     * rather than a re-implementation. No-op if no node is active.
     */
    public static void logStackTrace(Throwable throwable) {
        ExtentTest test = getTest();
        if (test != null) {
            test.fail(throwable);
        }
    }

    /**
     * Logs {@code label} (as a bold, neutral-grey badge, not plain text - see
     * {@link #coloredLabel}) followed by {@code content} in a monospace, whitespace-preserving
     * code block - for content (e.g. a JSON request body, a formatted header list) where a plain
     * {@link #logInfo(String)} line would collapse all whitespace once rendered in a browser:
     * Extent's Spark theme places a log message inside a plain HTML {@code <td>} with no
     * {@code white-space: pre} of its own, so even an already pretty-printed, multi-line body
     * would still render as one squished line. {@link MarkupHelper#createCodeBlock(String)}
     * renders into a {@code <textarea readonly>} instead, which preserves whitespace/newlines by
     * native HTML semantics regardless of network access - unlike the seemingly-more-correct
     * {@code createCodeBlock(String, CodeLanguage)} overload (an interactive syntax-highlighted
     * tree), which depends on a {@code jsontree.js} pulled from a CDN link Extent's own Spark
     * HTML embeds (verified live: reachable, and this report's own Pass/Fail badges already
     * depend on that same CDN's stylesheet for their color, so this isn't a new dependency this
     * class is introducing) - not used here regardless, since the body is the one thing this
     * report must stay readable for even with no network access, and plain preserved whitespace
     * already reads fine. No-op if no node is active. Labeled in blue - an outgoing request has
     * no pass/fail of its own to signal (that's what {@link #logStatusLine}/the two-arg overload
     * below are for on the response side), so it gets its own distinct, neutral-but-not-drab
     * color rather than reusing grey, which read as flat/washed-out against the green/amber/red
     * response labels next to it.
     */
    public static void logCodeBlock(String label, String content) {
        logCodeBlock(label, content, ExtentColor.BLUE);
    }

    /** {@link #logCodeBlock(String, String)}, with the label badge colored by {@code statusCode} (see {@link #colorForStatus}) instead of always grey - e.g. the Response Body block, so a failing call's body is visually flagged the same way its status line is. */
    public static void logCodeBlock(String label, String content, int statusCode) {
        logCodeBlock(label, content, colorForStatus(statusCode));
    }

    private static void logCodeBlock(String label, String content, ExtentColor labelColor) {
        ExtentTest test = getTest();
        if (test == null) {
            return;
        }
        test.info(coloredLabel(label, labelColor));
        test.log(Status.INFO, MarkupHelper.createCodeBlock(content));
    }

    /**
     * Logs {@code message} as a colored badge - green for a 2xx status, red for 4xx/5xx, amber
     * for 3xx, grey otherwise (see {@link #colorForStatus}) - the same at-a-glance signal
     * {@code assertStatusCode}'s own PASS/FAIL step already gives, but for the raw response
     * status line itself, shown regardless of whether the test goes on to assert anything about
     * it. No-op if no node is active.
     */
    public static void logStatusLine(String message, int statusCode) {
        ExtentTest test = getTest();
        if (test == null) {
            return;
        }
        test.log(Status.INFO, coloredLabel(message, colorForStatus(statusCode)));
    }

    private static Markup coloredLabel(String text, ExtentColor color) {
        return MarkupHelper.createLabel(text, color);
    }

    /** 2xx -&gt; green, 3xx -&gt; amber, 4xx/5xx -&gt; red, anything else -&gt; grey. */
    private static ExtentColor colorForStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return ExtentColor.GREEN;
        }
        if (statusCode >= 300 && statusCode < 400) {
            return ExtentColor.AMBER;
        }
        if (statusCode >= 400) {
            return ExtentColor.RED;
        }
        return ExtentColor.GREY;
    }

    /**
     * {@link ConfigKeys#REPORT_OVERWRITE} (default {@code true}, matching historical behavior):
     * {@code true} always writes {@code reports/extent/index.html}, so every run replaces the
     * last one - simplest for CI, where each run's artifacts are already uploaded separately
     * (see {@code .github/workflows/ci.yml}). {@code false} gives each run its own
     * {@code reports/extent/report-{yyyyMMdd-HHmmss}.html} instead, so a developer iterating
     * locally can keep several runs' reports side by side to compare rather than the next run
     * silently erasing the last one.
     */
    private static Path resolveReportPath() {
        boolean overwrite = ConfigManager.getBoolean(ConfigKeys.REPORT_OVERWRITE, true);
        String fileName = overwrite ? "index.html" : "report-" + REPORT_TIMESTAMP_FORMAT.format(LocalDateTime.now()) + ".html";
        return Path.of("reports", "extent", fileName);
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
