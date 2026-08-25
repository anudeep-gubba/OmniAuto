package com.framework.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.driver.WebDriverManager;
import com.framework.reporting.ExtentManager;
import com.framework.secrets.SecretManager;
import com.framework.utils.TextUtils;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Creates and finalizes one Extent report node per {@code @Test} method invocation
 * (requirement.md &sect;17/&sect;18), and flushes the report to disk.
 *
 * <p><b>Scope decision:</b> the node's lifecycle is bound to the {@code @Test} method's own
 * invocation window only (mirroring {@link DriverCleanupListener}'s already-proven
 * {@code isTestMethod()} pattern in this codebase) - {@code @BeforeMethod}/{@code @AfterMethod}
 * steps (e.g. an API test's login-in-{@code @BeforeMethod}) are <em>not</em> captured into
 * Extent here. Capturing those correctly would require knowing, from inside a
 * {@code @BeforeMethod} invocation, which upcoming {@code @Test} method it belongs to -
 * TestNG's public API does not reliably expose that (a class can share one
 * {@code @BeforeMethod} across several {@code @Test} methods), and this project's own rule is
 * to verify TestNG behavior empirically rather than assume it (see the
 * {@code testng-listener-ordering-gotcha} project note for what happened the one time that
 * wasn't done). Allure's report does not have this gap: {@code allure-testng}'s own listener
 * captures {@code @BeforeMethod}/{@code @AfterMethod} as native "Before"/"After" sections
 * regardless of any code here.</p>
 *
 * <p><b>Registration order matters</b> (see {@link ScreenshotCaptureListener}'s javadoc for
 * the empirically-confirmed reverse-order {@code afterInvocation} rule): listed in
 * {@code META-INF/services/org.testng.ITestNGListener} <em>before</em>
 * {@link ScreenshotCaptureListener}, so this listener's {@code afterInvocation} (which detaches
 * the current test node) fires <em>after</em> {@link ScreenshotCaptureListener}'s (which
 * attaches a failure screenshot to that still-open node) - and before
 * {@link DriverCleanupListener}'s, which does not depend on Extent state either way.</p>
 *
 * <p><b>Found in practice:</b> {@link ConfigManager}/{@link SecretManager} each log one
 * startup message the very first time anything touches them (config-loaded,
 * {@code .secret.env}-loaded) - ordinary {@code com.framework} logging, so
 * {@link com.framework.reporting.ExtentLoggingAppender} mirrors it like anything else. Because
 * that first touch happens lazily, wherever it happens to fall, an unlucky run put both
 * messages inside some unrelated test's own step log - e.g. a login test's report showing a
 * {@code .secret.env loaded...} line with nothing to do with logging in. {@link #onStart}
 * forces both to initialize here, before any {@code @Test} method - and therefore before any
 * Extent node - exists, so the appender's own existing "drop it if no test is active" rule
 * (see its javadoc) now correctly applies to these two as well. They still reach the console/
 * file logs exactly as before; only the misattributed Extent-report entry is prevented.</p>
 */
public class ExtentReportingListener implements IInvokedMethodListener, ITestListener {

    @Override
    public void onStart(ITestContext context) {
        ConfigManager.getBrowser();
        SecretManager.has("__report_clarity_warmup__");
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod() || isApiTest(method)) {
            return;
        }
        String className = method.getTestMethod().getRealClass().getSimpleName();
        String methodName = method.getTestMethod().getMethodName();
        // TextUtils.humanize() is only for the report title text - the method name itself is
        // untouched, so -Dtest=Class#method selection, IDE navigation, and the console's
        // [%X{test}] correlation tag (see logback.xml, deliberately left as the compact
        // Class.method form for fast grep-ability) all keep working exactly as before.
        String name = className + " — " + TextUtils.humanize(methodName);
        // Best-effort, not guaranteed: only shows up when @BeforeMethod already created the
        // driver before this @Test's own beforeInvocation fires (the common case - login/setup
        // usually happens in @BeforeMethod) - see assignRuntimeCategory's javadoc for the
        // always-correct fallback (afterInvocation, once the driver is certainly active).
        // Matters most for MultiDeviceParallelTest's own -Dparallel matrix, where two
        // concurrently-running rows on the same platform (e.g. "iPhone 17 Pro" vs "iPhone 17")
        // would otherwise render as identical, indistinguishable titles.
        if (MobileDriverManager.isDriverActive()) {
            var capabilities = MobileDriverManager.getDriver().getCapabilities();
            Object deviceName = capabilities.getCapability("deviceName");
            name += " [" + capabilities.getPlatformName() + (deviceName != null ? " · " + deviceName : "") + "]";
        }
        Integer retryAttempt = RetryAnalyzer.CURRENT_ATTEMPT.get();
        RetryAnalyzer.CURRENT_ATTEMPT.remove();
        if (retryAttempt != null && retryAttempt > 0) {
            name += " (Retry " + retryAttempt + ")";
        }

        // Audit finding, verified live: startTest() returns null when Extent is disabled
        // (report.types excludes "extent" - see ExtentManager's own javadoc), and this loop
        // dereferenced it unconditionally - every single test failed with a
        // NullPointerException the moment someone actually ran with Extent disabled, since
        // that path had never been exercised until now.
        ExtentTest test = ExtentManager.startTest(name);
        if (test == null) {
            return;
        }
        for (String group : method.getTestMethod().getGroups()) {
            test.assignCategory(group);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod() || isApiTest(method)) {
            return;
        }
        ExtentTest test = ExtentManager.getTest();
        if (test != null) {
            finalizeStatus(test, testResult);
            assignRuntimeCategory(test);
        }
        ExtentManager.endTest();
    }

    private static void finalizeStatus(ExtentTest test, ITestResult testResult) {
        switch (testResult.getStatus()) {
            case ITestResult.SUCCESS -> test.pass("Test passed.");
            case ITestResult.FAILURE -> {
                if (testResult.getThrowable() != null) {
                    test.fail(testResult.getThrowable());
                } else {
                    test.fail("Test failed.");
                }
            }
            case ITestResult.SKIP -> test.skip("Test skipped.");
            default -> { /* CREATED/STARTED: TestNG never reports a finished result in these states. */ }
        }
    }

    /**
     * Browser/platform tagging - only possible once a driver actually exists, i.e. by now, not
     * at node creation (see {@link #beforeInvocation}'s own best-effort title version, which
     * runs too early to be guaranteed one is active yet).
     *
     * <p><b>Found in practice, not assumed:</b> a plain {@code "mobile"} category told a report
     * reader nothing about which platform actually ran - indistinguishable from any other mobile
     * test regardless of {@code -Dmobile.platform=ios} vs {@code android}, unlike Web's own
     * {@code ConfigManager.getBrowser()} tag right above. Reads the live driver's own
     * capabilities rather than {@link ConfigManager}'s {@code mobile.platform}/
     * {@code mobile.device.name}: those are thread-local overrides
     * ({@link com.framework.driver.DriverFactory#assignDeviceFromPool}) that {@link
     * ConfigParameterListener#beforeInvocation} already clears before the {@code @Test} method
     * itself runs (it resets tier-4/5 state before <em>every</em> invoked method, not just this
     * one) - reading them here would silently fall back to the global default on a {@code
     * -Dparallel} device-pool run, exactly the case (several concurrent platforms/devices) this
     * exists to distinguish. The capabilities baked into the already-created driver have no such
     * lifetime problem - same source {@link ScreenshotCaptureListener#attachMobileFailureDetail}
     * already trusts for the same information.</p>
     */
    private static void assignRuntimeCategory(ExtentTest test) {
        if (WebDriverManager.isDriverActive()) {
            test.assignCategory(ConfigManager.getBrowser());
        } else if (MobileDriverManager.isDriverActive()) {
            var capabilities = MobileDriverManager.getDriver().getCapabilities();
            test.assignCategory(String.valueOf(capabilities.getPlatformName()));
            Object deviceName = capabilities.getCapability("deviceName");
            if (deviceName != null) {
                test.assignCategory(String.valueOf(deviceName));
            }
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentManager.flush();
    }

    /** {@code true} for every API test ({@code "api"} TestNG group - see {@link ApiTestReportListener}'s javadoc) - those get their own self-contained HTML report instead and are deliberately excluded from Extent entirely, not just left detail-free. */
    private static boolean isApiTest(IInvokedMethod method) {
        for (String group : method.getTestMethod().getGroups()) {
            if ("api".equals(group)) {
                return true;
            }
        }
        return false;
    }
}
