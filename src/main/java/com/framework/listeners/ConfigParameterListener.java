package com.framework.listeners;

import com.framework.config.ConfigManager;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Bridges TestNG {@code <parameter>} values (suite/test XML) into
 * {@link ConfigManager}'s tier-4 thread-local configuration layer.
 *
 * <p>Auto-registered via {@code META-INF/services/org.testng.ITestNGListener}
 * (the {@link java.util.ServiceLoader} convention TestNG uses for listener
 * discovery) so no suite XML needs to declare it explicitly.</p>
 *
 * <p><b>Found in practice, not assumed</b> (Phase 12): this originally reset/repopulated
 * thread-local state in {@code ITestListener.onTestStart}, which - per the empirically
 * confirmed ordering documented on {@link ApiContextListener} - fires <em>after</em>
 * {@code @BeforeMethod}. That was invisible under sequential execution, but a real
 * {@code mvn test -Dparallel=classes -DthreadCount=4} stress run caught it directly:
 * {@code EventsTest} creates its WebDriver inside {@code @BeforeMethod}, so it was
 * always reading whichever tier-4/5 config a <em>different</em> test last left on that pooled
 * thread (or the hardcoded default, on a thread's very first use) instead of the requested
 * {@code -Dheadless=true} - {@code EventsTest}'s browser windows were visibly non-headless in
 * a run that asked for headless. Fixed by switching to
 * {@link IInvokedMethodListener} and repopulating <em>before every single invoked method</em>
 * ({@code @BeforeMethod}, the {@code @Test} itself, {@code @AfterMethod} alike) rather than
 * trying to pick the one "right" TestNG hook per method type - clearing and resetting are
 * cheap and idempotent, so there is no real cost to doing it unconditionally, and no more
 * ordering question to get wrong.</p>
 *
 * <p>{@link #onFinish(ITestContext)} remains a final safety-net cleanup once a {@code <test>}
 * tag's threads are done (requirement.md &sect;33 - ThreadLocal must be removed).</p>
 */
public class ConfigParameterListener implements IInvokedMethodListener, ITestListener {

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        ConfigManager.clearThreadState();
        ConfigManager.setTestNgParameters(testResult.getTestContext().getCurrentXmlTest().getAllParameters());
    }

    @Override
    public void onFinish(ITestContext context) {
        ConfigManager.clearThreadState();
    }
}
