package com.framework.listeners;

import com.framework.context.VariableManager;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Clears {@link VariableManager}'s thread-local runtime variables (API
 * chaining state, including the current bearer token via {@code ApiContext})
 * around every test method, so a pooled thread reused under
 * {@code parallel="methods"} never leaks one test's chained
 * {@code userId}/{@code accessToken}/etc. into the next (requirement.md
 * &sect;11/&sect;33).
 *
 * <p><b>Hook choice, confirmed empirically rather than assumed</b> (this
 * project has been burned by a wrong TestNG-ordering assumption before - see
 * {@link ScreenshotCaptureListener}'s javadoc): {@code ITestListener}'s
 * {@code onTestStart} looks like the obvious "clear before the test" hook,
 * but a throwaway probe listener showed it actually fires <em>after</em>
 * {@code @BeforeMethod} has already run:</p>
 *
 * <pre>
 * beforeInvocation(before-config) -&gt; @BeforeMethod -&gt; afterInvocation(before-config)
 * -&gt; onTestStart -&gt; beforeInvocation(test) -&gt; @Test -&gt; afterInvocation(test)
 * -&gt; onTestSuccess -&gt; beforeInvocation(after-config) -&gt; @AfterMethod -&gt; afterInvocation(after-config)
 * </pre>
 *
 * <p>Clearing on {@code onTestStart} would wipe out exactly the kind of
 * context a {@code @BeforeMethod} commonly sets up (e.g. logging in and
 * storing the token) before the {@code @Test} body ever runs - confirmed the
 * hard way when {@code EventBookingChainingTest}'s
 * {@code @BeforeMethod}-driven login started failing with 401s after this
 * listener was first wired up with {@code onTestStart}. Using
 * {@link IInvokedMethodListener} and checking
 * {@link org.testng.ITestNGMethod#isBeforeMethodConfiguration()} /
 * {@link org.testng.ITestNGMethod#isAfterMethodConfiguration()} instead
 * clears at the true boundaries: immediately before any
 * {@code @BeforeMethod} runs, and immediately after any {@code @AfterMethod}
 * has finished (so cleanup code in {@code @AfterMethod} - like cancelling a
 * booking - can still read the context it needs).</p>
 *
 * <p>Auto-registered via {@code META-INF/services/org.testng.ITestNGListener}
 * so no suite XML needs to declare it explicitly.</p>
 */
public class ApiContextListener implements IInvokedMethodListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (method.getTestMethod().isBeforeMethodConfiguration()) {
            VariableManager.clear();
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (method.getTestMethod().isAfterMethodConfiguration()) {
            VariableManager.clear();
        }
    }
}
