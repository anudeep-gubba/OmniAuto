package com.framework.listeners;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * A controlled, bounded retry (requirement.md &sect;23) - assigned to every {@code @Test}
 * method automatically by {@link RetryAnalyzerTransformer}, so test authors never write
 * {@code @Test(retryAnalyzer = RetryAnalyzer.class)} themselves.
 *
 * <p><b>Never retries an assertion failure</b> ({@link AssertionError}, what every TestNG/
 * Hamcrest/AssertJ assertion throws) - that is a genuine business/logic failure, not
 * transient infrastructure noise, and requirement.md &sect;23 explicitly asks to "avoid
 * retrying known assertion/business failures where appropriate." Anything else (a flaky
 * element interaction, a dropped connection, {@link com.framework.exceptions.ApiException},
 * {@link com.framework.exceptions.ElementInteractionException}, a raw Selenium/Appium
 * exception, ...) is retried up to {@code retry.max.count} times.</p>
 *
 * <p>Only ever attached to {@code @Test} methods - TestNG has no {@code retryAnalyzer} concept
 * for {@code @Before}/{@code @After} configuration methods. {@link ConfigurationRetryListener}
 * extends the same policy (same {@code retry.max.count}, same never-retry-an-{@link
 * AssertionError} rule) to {@code @BeforeMethod}/{@code @AfterMethod} - see its own javadoc for
 * why that gap mattered in practice.</p>
 *
 * <p>Each retry attempt is logged clearly (never silently) and, via
 * {@link #CURRENT_ATTEMPT}, labeled in the Extent report by
 * {@link ExtentReportingListener} as e.g. "LoginTest.validLogin (Retry 1)" - a failed
 * initial attempt keeps its own report entry rather than being overwritten by the retry
 * that follows it (&sect;23: "never hide the original failure"). Allure's retry grouping is
 * automatic once a {@code retryAnalyzer} is set - no extra code needed there.</p>
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> TestNG creates one
 * {@code RetryAnalyzer} instance per {@code @Test} method (via
 * {@link RetryAnalyzerTransformer}, evaluated per method), so the instance field
 * {@link #retryCount} is <b>test-scoped</b> (category 4), never shared across methods or
 * threads. {@link #CURRENT_ATTEMPT} is the one piece of state genuinely shared across
 * classes (with {@link ExtentReportingListener}) - a <b>thread-local</b> (category 3)
 * read-once/cleared-once handoff, safe because a retry always re-invokes the same method on
 * the same thread immediately, with nothing else from this framework running on that thread
 * in between (see its own javadoc for the ordering this relies on).</p>
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryAnalyzer.class);

    /**
     * Set by {@link #retry(ITestResult)} right before a retry is scheduled; read-and-cleared
     * by {@link ExtentReportingListener} when it creates the retry attempt's report node.
     * Safe as a plain ThreadLocal handoff (not a queue) because TestNG retries the exact same
     * method on the exact same thread as its very next action - nothing else runs on this
     * thread between the two reads.
     */
    static final ThreadLocal<Integer> CURRENT_ATTEMPT = new ThreadLocal<>();

    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (result.getThrowable() instanceof AssertionError) {
            LOGGER.info("Not retrying '{}': failure was an assertion (business/logic failure), not transient.",
                    testLabel(result));
            return false;
        }

        int maxRetries = ConfigManager.getInt(ConfigKeys.RETRY_MAX_COUNT, 0);
        if (retryCount >= maxRetries) {
            return false;
        }

        retryCount++;
        CURRENT_ATTEMPT.set(retryCount);
        LOGGER.warn("Retrying '{}': attempt {}/{} after failure - {}",
                testLabel(result), retryCount, maxRetries,
                result.getThrowable() != null ? result.getThrowable().getMessage() : "unknown failure");
        return true;
    }

    private static String testLabel(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
    }
}
