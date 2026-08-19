package com.framework.reporting;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.framework.secrets.SensitiveDataMasker;

/**
 * Mirrors every {@code com.framework}/{@code com.tests} log event into the calling thread's
 * current Extent test node, so {@code WebActions}/{@code MobileActions}/{@code ApiClient}'s
 * (Phases 5-7) and Page Objects' (Phase 5/6) existing {@code logger.info(...)} calls become
 * Extent report steps automatically - requirement.md &sect;18: "Framework actions should
 * automatically be reportable... The test author should NOT need to manually add reporting
 * code for every framework action." No test/page-object/service code changes for this;
 * wired in purely through {@code logback.xml}.
 *
 * <p>Every message is passed through {@link SensitiveDataMasker#mask(String)} unconditionally
 * here, the same guarantee {@code com.framework.secrets.MaskingMessageConverter} gives
 * CONSOLE/FILE via the pattern layout - this appender does not use a pattern at all (it reads
 * {@code getFormattedMessage()} directly), so it calls the masker itself rather than relying on
 * a call site remembering to.</p>
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
        test.log(mapLevel(event.getLevel()), SensitiveDataMasker.mask(event.getFormattedMessage()));
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
