package com.framework.listeners;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IConfigurable;
import org.testng.IConfigureCallBack;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

/**
 * <p><b>Currently inert, left registered rather than deleted - read this before assuming it's
 * still protecting anything.</b> Since the Cucumber/BDD migration, {@code com.tests} has zero
 * plain TestNG {@code @BeforeMethod}/{@code @AfterMethod} methods anywhere: every per-scenario
 * setup/teardown is now a Cucumber {@code @Before}/{@code @After} hook
 * ({@code com.tests.hooks.*}), which runs as an ordinary method call <em>inside</em> the single
 * {@code runScenario} {@code @Test} invocation, not as a separate TestNG configuration-method
 * invocation - so {@link ITestNGMethod#isBeforeMethodConfiguration()}/
 * {@code isAfterMethodConfiguration()} never return {@code true} for anything in this suite
 * today, and the retry loop below never actually gets to retry anything in isolation. A
 * transient failure inside a Cucumber hook (e.g. {@code ApiHooks}' teardown hitting a momentary
 * 502) is only retried as part of {@link RetryAnalyzer}'s coarser whole-scenario retry on
 * {@code runScenario} itself - re-running the scenario's steps too, not just the failed hook.
 * Kept registered rather than removed because it is genuinely zero-cost while inert (one cheap
 * boolean check per configuration-method invocation, of which there are currently none) and
 * would immediately matter again the moment any test-scoped class anywhere adds a real
 * {@code @BeforeMethod}/{@code @AfterMethod} of its own.</p>
 *
 * <p>Extends {@code retry.max.count} (requirement.md &sect;23) to {@code @BeforeMethod}/
 * {@code @AfterMethod} - {@link RetryAnalyzer} only ever covers {@code @Test} methods (TestNG
 * has no {@code retryAnalyzer} concept for configuration methods at all, unlike {@code @Test}),
 * so a transient failure in a per-test setup/teardown step (e.g. an API test's seeded-account
 * login hitting a momentary 502 from the target server) previously failed outright with no
 * retry, unlike the exact same transient failure inside a {@code @Test} body.</p>
 *
 * <p><b>Found in practice, not assumed:</b> an 8-thread `-Dparallel=methods` run against the
 * live EventHub sandbox hit a `502 Proxy Error` twice mid-{@code @Test} (retried successfully
 * by {@link RetryAnalyzer}) and once inside {@code BaseApiTest#loginWithSeededAccount}, a
 * {@code @BeforeMethod} - which failed outright and skipped every other {@code @Test} in that
 * class, since nothing retried it.</p>
 *
 * <p>Scoped to {@code @BeforeMethod}/{@code @AfterMethod} only (via {@link
 * ITestNGMethod#isBeforeMethodConfiguration()}/{@link ITestNGMethod#isAfterMethodConfiguration()}) -
 * not {@code @Before/@AfterClass}/{@code @Before/@AfterSuite}/{@code @Before/@AfterGroups},
 * which run once for many tests rather than fresh per test, where a partially-completed retry
 * carries a different (and less obviously safe) risk profile. Same policy as {@link
 * RetryAnalyzer} otherwise: never retries an {@link AssertionError} (a genuine business/logic
 * failure, not transient infrastructure noise), bounded by the same {@link
 * ConfigKeys#RETRY_MAX_COUNT}, and every retry is logged clearly, never silently.</p>
 *
 * <p>Applies framework-wide (Web/Mobile/API alike) - a transient failure in any per-test setup/
 * teardown step deserves the same retry policy a {@code @Test} body already gets, not just the
 * API login case that surfaced this gap.</p>
 *
 * <p><b>Verified against TestNG 7.10.2's own source, not assumed:</b> {@link
 * IConfigureCallBack#runConfigurationMethod} never throws back to {@link #run} - TestNG's
 * {@code MethodInvocationHelper.invokeConfigurable} wraps it in a try/catch that stashes the
 * failure via {@code testResult.setThrowable(t)} (clearing it to {@code null} on a success) and
 * returns normally either way; only <em>after</em> {@link #run} itself returns does that caller
 * inspect its own captured copy of the same throwable and re-throw it, which is what finally
 * turns into the {@code FAILURE} status. So this reads {@link ITestResult#getThrowable()} after
 * each attempt to decide pass/fail/retry - not a try/catch around {@code
 * runConfigurationMethod}, and not {@link ITestResult#getStatus()} either, which stays {@code
 * STARTED} for the whole duration of this method, only set by the caller once it returns (a
 * first version tried both of those and never once observed a failure - the method ran exactly
 * once, no matter how many retries were configured).</p>
 *
 * <p>Self-registers via {@code META-INF/services/org.testng.ITestNGListener} - {@link
 * IConfigurable} is itself an {@code ITestNGListener} subtype, discovered the same {@link
 * java.util.ServiceLoader} way as every other listener in this package. TestNG supports only one
 * active {@link IConfigurable} per run; nothing else in this framework implements it.</p>
 */
public class ConfigurationRetryListener implements IConfigurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationRetryListener.class);

    @Override
    public void run(IConfigureCallBack callBack, ITestResult testResult) {
        ITestNGMethod method = testResult.getMethod();
        if (!method.isBeforeMethodConfiguration() && !method.isAfterMethodConfiguration()) {
            callBack.runConfigurationMethod(testResult);
            return;
        }

        int maxRetries = ConfigManager.getInt(ConfigKeys.RETRY_MAX_COUNT, 0);
        int attempt = 0;
        while (true) {
            callBack.runConfigurationMethod(testResult);
            Throwable raw = testResult.getThrowable();
            if (raw == null) {
                return;
            }
            // Unwrap the same way ConfigInvoker#throwConfigurationFailure does for the final
            // failure's own testResult.getThrowable() (verified against TestNG source): a
            // reflectively-invoked configuration method's real exception arrives here wrapped in
            // InvocationTargetException, whose own getMessage() is always null and which is
            // never itself an AssertionError even when its cause is - checking/logging the raw
            // wrapper instead of unwrapping first would both retry a genuine assertion failure
            // (wrong - see RetryAnalyzer's own policy) and log every retry as "failure - null".
            Throwable cause = raw.getCause() != null ? raw.getCause() : raw;
            if (cause instanceof AssertionError) {
                LOGGER.info("Not retrying '{}': failure was an assertion (business/logic failure), not transient.",
                        label(testResult));
                return;
            }
            if (attempt >= maxRetries) {
                return;
            }
            attempt++;
            LOGGER.warn("Retrying '{}': attempt {}/{} after failure - {}",
                    label(testResult), attempt, maxRetries, cause.getMessage());
        }
    }

    private static String label(ITestResult testResult) {
        return testResult.getTestClass().getRealClass().getSimpleName() + "." + testResult.getMethod().getMethodName();
    }
}
