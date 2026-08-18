package com.framework.reporting;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;

/**
 * Mirrors every {@code com.framework}/{@code com.tests} log event into the calling thread's
 * current Extent test node, so {@code WebActions}/{@code MobileActions}/{@code ApiClient}'s
 * (Phases 5-7) and Page Objects' (Phase 5/6) existing {@code logger.info(...)} calls become
 * Extent report steps automatically - requirement.md &sect;18: "Framework actions should
 * automatically be reportable... The test author should NOT need to manually add reporting
 * code for every framework action." No test/page-object/service code changes for this;
 * wired in purely through {@code logback.xml}.
 *
 * <p>A useful side effect of piggybacking on the logging pipeline rather than a separate
 * reporting call: every message here has already passed through
 * {@link com.framework.secrets.SensitiveDataMasker#mask(String)} wherever the log call site
 * does that (see the {@code masking-is-opt-in-per-call-site} project note) <em>before</em> it
 * ever reaches this appender, so the report inherits the same masking guarantee for free -
 * nothing extra to remember here.</p>
 *
 * <p>Silently drops events when no Extent test is active on the calling thread (i.e. outside
 * a {@code @Test} method's own invocation window - see {@link ExtentReportingListener}'s
 * javadoc for that scope decision) rather than logging a warning about it, since that is the
 * expected, common case (framework startup logging, {@code @BeforeSuite}, ...), not an error.</p>
 */
public class ExtentLoggingAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        ExtentTest test = ExtentManager.getTest();
        if (test == null) {
            return;
        }
        test.log(mapLevel(event.getLevel()), event.getFormattedMessage());
    }

    private static Status mapLevel(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return Status.FAIL;
        }
        if (level.isGreaterOrEqual(Level.WARN)) {
            return Status.WARNING;
        }
        return Status.INFO;
    }
}
