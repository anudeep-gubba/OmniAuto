package com.framework.secrets;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback conversion word that masks a log event's formatted message through
 * {@link SensitiveDataMasker#mask(String)} before it reaches an appender's output.
 *
 * <p>Closes the framework's previously-documented gap that masking was <em>opt-in per call
 * site</em> - a {@code logger.info(...)} call anywhere under {@code com.framework}/
 * {@code com.tests} had to remember to call {@link SensitiveDataMasker#mask(String)} itself, or
 * a secret went out unmasked. Wired into {@code logback.xml} as the {@code %maskedMsg}
 * conversion word in place of the standard {@code %msg}, this masks every line through
 * CONSOLE/FILE unconditionally, regardless of whether the call site remembered to. Existing
 * call-site {@code .mask()} calls (e.g. {@link com.framework.api.ApiClient}'s request/response
 * logging) are still needed for their own purpose - the same already-masked string is reused for
 * the matching Allure attachment, a separate pathway this converter does not touch - and are
 * harmless here since masking an already-masked string is idempotent.</p>
 *
 * <p>Only protects appenders that render through a {@code PatternLayout}/encoder referencing this
 * conversion word (CONSOLE, FILE). {@link com.framework.reporting.ExtentLoggingAppender} does not
 * use a pattern at all - it is a custom {@code AppenderBase} that reads {@code getFormattedMessage()}
 * directly - so it calls {@link SensitiveDataMasker#mask(String)} itself for the same guarantee. A
 * raw, programmatically-attached appender that reads {@code ILoggingEvent} directly (e.g. a test's
 * {@code ListAppender} probe) similarly bypasses this converter, same as it always bypassed
 * per-call-site masking, too - that is why {@code LoggingMaskingIntegrationTest} still relies on
 * (and still needs) real call-site masking, not this converter.</p>
 */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(event.getFormattedMessage());
    }
}
