package com.framework.listeners;

import com.framework.secrets.SensitiveDataMasker;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Closes a documented gap (README "Known limitations"): {@code allure-testng} records every
 * {@code @DataProvider} row as an Allure "Parameters" entry using that row object's own
 * {@code toString()} - completely bypassing every masking call site this framework controls
 * ({@link com.framework.secrets.MaskingMessageConverter} for Logback lines,
 * {@link com.framework.reporting.ExtentLoggingAppender} for Extent). A row built from
 * {@link com.framework.testdata.TestData#dataProvider(Class)} carries fully placeholder-resolved
 * values (see {@code TestData}'s javadoc on when resolution happens) - so a row type with a
 * secret field (e.g. an {@code EVENTHUB_EMAIL}/{@code EVENTHUB_PASSWORD}-backed record) would
 * otherwise leak that real value straight into {@code allure-results/*.json} the moment someone
 * uses it as a {@code @DataProvider} row, via nothing more than that record's ordinary
 * auto-generated {@code toString()}.
 *
 * <p><b>Fixed systemically, not per-row-type</b> - the same design choice already made for
 * Logback/Extent output: rather than requiring every current and future {@code *TestCase}/
 * {@code *Data} record used as a row to remember a hand-written masking {@code toString()},
 * this masks whatever {@code allure-testng} already captured, once, centrally, using the same
 * {@link SensitiveDataMasker#mask(String)} every other report path uses - a new row type is
 * safe by default, not only safe if its author remembers to opt in.</p>
 *
 * <p><b>Why {@link IInvokedMethodListener#afterInvocation} and not {@code ITestListener}:</b>
 * {@code allure-testng}'s own listener writes the result JSON (masked value included) from
 * {@code ITestListener.onTestSuccess}/{@code onTestFailure}/{@code onTestSkipped}. Those fire
 * only after every registered {@code IInvokedMethodListener}'s {@code afterInvocation} has
 * already run for that method - a phase-ordering guarantee of TestNG's own invocation pipeline
 * (method invocation completes -&gt; every {@code afterInvocation} -&gt; only then the
 * result-based {@code ITestListener} callbacks), independent of which jar registered which
 * listener or in what order (unlike the same-hook-type ordering {@link ScreenshotCaptureListener}
 * documents, which does depend on registration order). Masking here is therefore guaranteed to
 * land before {@code allure-testng} ever serializes the parameters to disk, regardless of
 * classpath/service-loader order between this framework's listeners and {@code allure-testng}'s
 * own.</p>
 */
public class AllureParameterMaskingListener implements IInvokedMethodListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureParameterMaskingListener.class);

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        try {
            Allure.getLifecycle().updateTestCase(allureResult ->
                    allureResult.getParameters().forEach(parameter ->
                            parameter.setValue(SensitiveDataMasker.mask(parameter.getValue()))));
        } catch (RuntimeException e) {
            // Best-effort, same convention as AllureManager: a reporting-infra hiccup must never
            // fail an otherwise-passing test, and must never be the reason a secret goes unmasked
            // silently - so a failure here is logged loudly rather than swallowed quietly.
            LOGGER.warn("Failed to mask Allure parameters for '{}': {}", testResult.getName(), e.getMessage());
        }
    }
}
