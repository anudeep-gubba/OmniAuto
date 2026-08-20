package com.framework.reporting;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.framework.secrets.SensitiveDataMasker;
import io.qameta.allure.Allure;

/**
 * Mirrors every business-narrative log event (Web/Mobile Page Objects and Components, API
 * Services - the same loggers {@link ExtentLoggingAppender} already covers) into Allure as its
 * own step, closing a real, previously-documented gap: {@code allure-testng} has no bridge for
 * arbitrary {@code logger.info(...)} calls the way the Extent side does, so before this, an
 * Allure reader saw request/response detail for API calls (explicit {@code Allure.step} calls
 * in {@code ApiClient}) but nothing narrative at all for a Web/Mobile test - no "entered email",
 * no "clicked Sign In", just a bare pass/fail. Wired purely through {@code logback.xml}, same as
 * {@link ExtentLoggingAppender} - no test/page-object/service code changes needed for this.
 *
 * <p><b>Deliberately not wired to {@code com.framework.api}</b> - {@code ApiClient} already
 * gets its own richer, explicit {@code Allure.step} per call (with request/response attachments
 * nested under it, see {@code ApiClient#execute}); mirroring its log lines here too would
 * duplicate that as a second, flatter, unattached step for the same call.</p>
 *
 * <p>No-ops when Allure is disabled ({@link ReportManager#isAllureEnabled()}) or when there is
 * no active Allure test case/step on the calling thread ({@link
 * io.qameta.allure.AllureLifecycle#getCurrentTestCaseOrStep()} - the direct analogue of {@link
 * ExtentLoggingAppender}'s own {@code ExtentManager.getTest() == null} guard, needed for the
 * same reason: framework startup logging, {@code @BeforeSuite}, anything outside a running
 * {@code @Test}'s own invocation window, is the expected common case here, not an error).</p>
 */
public class AllureLoggingAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        if (!ReportManager.isAllureEnabled() || Allure.getLifecycle().getCurrentTestCaseOrStep().isEmpty()) {
            return;
        }
        Allure.step(SensitiveDataMasker.mask(event.getFormattedMessage()));
    }
}
