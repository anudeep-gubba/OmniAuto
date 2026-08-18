package com.framework.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.driver.WebDriverManager;
import com.framework.reporting.ExtentManager;
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
 */
public class ExtentReportingListener implements IInvokedMethodListener, ITestListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        String name = method.getTestMethod().getRealClass().getSimpleName() + "." + method.getTestMethod().getMethodName();
        Integer retryAttempt = RetryAnalyzer.CURRENT_ATTEMPT.get();
        RetryAnalyzer.CURRENT_ATTEMPT.remove();
        if (retryAttempt != null && retryAttempt > 0) {
            name += " (Retry " + retryAttempt + ")";
        }

        ExtentTest test = ExtentManager.startTest(name);
        for (String group : method.getTestMethod().getGroups()) {
            test.assignCategory(group);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
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

    /** Best-effort browser/platform tagging - only possible once a driver actually exists, i.e. by now, not at node creation. */
    private static void assignRuntimeCategory(ExtentTest test) {
        if (WebDriverManager.isDriverActive()) {
            test.assignCategory(ConfigManager.getBrowser());
        } else if (MobileDriverManager.isDriverActive()) {
            test.assignCategory("mobile");
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentManager.flush();
    }
}
