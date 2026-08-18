package com.framework.listeners;

import com.framework.driver.MobileDriverManager;
import com.framework.driver.WebDriverManager;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Quits any active WebDriver/AppiumDriver and clears its ThreadLocal storage
 * after every {@code @Test} method, regardless of pass/fail/skip
 * (requirement.md &sect;33). Test authors never call {@code quitDriver()}
 * themselves.
 *
 * <p>Uses {@link IInvokedMethodListener} rather than {@code ITestListener}
 * specifically because {@code afterInvocation} fires unconditionally on
 * outcome, avoiding three near-identical hooks
 * (onTestSuccess/onTestFailure/onTestSkipped). {@code isTestMethod()} filters
 * out {@code @Before}/{@code @After} configuration methods, which should not
 * trigger a mid-suite driver quit.</p>
 *
 * <p>Auto-registered via {@code META-INF/services/org.testng.ITestNGListener}.</p>
 */
public class DriverCleanupListener implements IInvokedMethodListener {

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        if (WebDriverManager.isDriverActive()) {
            WebDriverManager.quitDriver();
        }
        if (MobileDriverManager.isDriverActive()) {
            MobileDriverManager.quitDriver();
        }
    }
}
