package com.tests.base;

import com.framework.exceptions.ElementInteractionException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Phase 11 validation for {@link com.framework.listeners.RetryAnalyzer} /
 * {@link com.framework.listeners.RetryAnalyzerTransformer} (requirement.md &sect;23): proves,
 * through a real TestNG run rather than a unit test of {@code RetryAnalyzer} in isolation,
 * that the globally-applied retry policy actually retries a non-assertion failure and
 * actually does <em>not</em> retry an assertion failure. Neither test declares its own
 * {@code retryAnalyzer} - both rely on {@link com.framework.listeners.RetryAnalyzerTransformer}
 * assigning one automatically, the real end-to-end path every other test in this suite goes
 * through too.
 *
 * <p><b>Deliberately excluded from the default run</b> via the {@code frameworkSelfTest}
 * group: {@link #assertionFailureIsNeverRetried()} is designed to fail on every single
 * invocation - that is the point, proving the negative case with a real assertion failure
 * rather than a simulated one - so a plain {@code mvn test} would otherwise always end red
 * because of a passing check, not a real regression. Same excludable-group pattern already
 * used for {@code mobile} when that infra isn't up:
 * {@code mvn test -DexcludedGroups=mobile,frameworkSelfTest} for an all-green run, or
 * {@code mvn test -Dgroups=frameworkSelfTest} to validate this behavior on demand.</p>
 */
public class RetryBehaviorTest {

    private static final AtomicInteger NON_ASSERTION_ATTEMPTS = new AtomicInteger(0);
    private static final AtomicInteger ASSERTION_ATTEMPTS = new AtomicInteger(0);

    /** Fails with a non-{@link AssertionError} on attempt 1; {@code retry.max.count=1} means attempt 2 must happen and pass. */
    @Test(groups = "frameworkSelfTest")
    public void nonAssertionFailurePassesAfterOneRetry() {
        int attempt = NON_ASSERTION_ATTEMPTS.incrementAndGet();
        if (attempt == 1) {
            throw new ElementInteractionException("Simulated transient failure on attempt 1 - RetryAnalyzer should retry this.");
        }
        assertEquals(attempt, 2, "Reaching attempt 2 at all is the proof RetryAnalyzer retried the first failure.");
    }

    /** Always fails via a genuine assertion - {@link #verifyAssertionFailureWasNeverRetried()} confirms it ran exactly once. */
    @Test(groups = "frameworkSelfTest")
    public void assertionFailureIsNeverRetried() {
        ASSERTION_ATTEMPTS.incrementAndGet();
        Assert.fail("Deliberately fails on every invocation to prove RetryAnalyzer does not retry AssertionError "
                + "(requirement.md section 23: avoid retrying known assertion/business failures).");
    }

    /** {@code alwaysRun = true} so this still executes even though its dependency above always fails. */
    @Test(groups = "frameworkSelfTest", dependsOnMethods = "assertionFailureIsNeverRetried", alwaysRun = true)
    public void verifyAssertionFailureWasNeverRetried() {
        assertEquals(ASSERTION_ATTEMPTS.get(), 1,
                "assertionFailureIsNeverRetried() must have run exactly once - a retry would have made this 2.");
    }
}
