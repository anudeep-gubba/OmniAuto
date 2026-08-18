package com.framework.listeners;

import org.slf4j.MDC;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

/**
 * Tags every log line with which TestNG method is currently producing it - {@code %X{test}}
 * in {@code logback.xml} - the one piece of context {@code %thread} alone stops giving you
 * once several tests share a pooled thread under {@code parallel="methods"}/{@code "classes"}
 * (requirement.md &sect;16 "detailed action-level logging" combined with &sect;20 parallel
 * execution).
 *
 * <p>Deliberately brackets <em>every</em> invoked method - {@code @BeforeMethod}, the
 * {@code @Test} itself, {@code @AfterMethod} - not just the {@code @Test} body: each one's
 * own {@code beforeInvocation}/{@code afterInvocation} pair tightly scopes the MDC value to
 * exactly the method producing log lines at that moment, so e.g. a login performed in
 * {@code @BeforeMethod} is correctly tagged {@code LoginTest.logIn}, not misattributed to the
 * {@code @Test} that hasn't started yet. This sidesteps the ordering pitfall
 * {@link ApiContextListener}'s javadoc documents in detail (there, clearing state at the
 * wrong lifecycle point broke authentication) - since every method brackets only its own
 * invocation, there's no "before the right point vs. after it" question to get wrong.</p>
 *
 * <p><b>Registration order matters:</b> listed <em>first</em> in
 * {@code META-INF/services/org.testng.ITestNGListener}, ahead of every other
 * {@code IInvokedMethodListener} here. Per {@link ScreenshotCaptureListener}'s javadoc,
 * TestNG invokes multiple {@code afterInvocation} listeners in <b>reverse</b> registration
 * order - so listing this one first means its {@code afterInvocation} (which removes the MDC
 * value) fires <em>last</em>, after every other listener's own {@code afterInvocation}
 * logging has already run with the tag still present. Being listed first also puts its
 * {@code beforeInvocation} first in normal (non-reversed) order, setting the tag before any
 * other listener's {@code beforeInvocation} could log anything.</p>
 *
 * <p>Auto-registered via {@code META-INF/services/org.testng.ITestNGListener}.</p>
 */
public class TestLoggingContextListener implements IInvokedMethodListener {

    private static final String MDC_KEY = "test";

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        MDC.put(MDC_KEY, method.getTestMethod().getRealClass().getSimpleName()
                + "." + method.getTestMethod().getMethodName());
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        MDC.remove(MDC_KEY);
    }
}
