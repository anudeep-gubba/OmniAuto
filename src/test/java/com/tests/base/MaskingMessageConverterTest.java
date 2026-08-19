package com.tests.base;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.CoreConstants;
import com.framework.secrets.MaskingMessageConverter;
import com.framework.secrets.SensitiveDataMasker;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Audit finding fix: masking was opt-in per log call site (nothing enforced it framework-wide -
 * see the now-updated javadoc on {@code LoggingMaskingIntegrationTest}). {@code %maskedMsg}
 * ({@link MaskingMessageConverter}, wired into {@code logback.xml} in place of {@code %msg}) now
 * masks CONSOLE/FILE output unconditionally, regardless of whether the call site remembered to.
 *
 * <p>Two levels of proof, matching this project's standing preference for live proof over
 * assumption:</p>
 * <ol>
 *     <li>{@link #convertsARawUnmaskedSecretShapedMessage()} - the converter in isolation,
 *     against an event whose message was deliberately never passed through
 *     {@link SensitiveDataMasker#mask(String)} by the caller.</li>
 *     <li>{@link #realPatternLayoutEncoderMasksThroughTheFullPipeline()} - a real
 *     {@link PatternLayoutEncoder} built with the exact {@code %maskedMsg} conversion word/
 *     pattern {@code logback.xml} declares, proving the wiring (not just the class) works -
 *     {@code LoggingMaskingIntegrationTest}'s {@code ListAppender} approach cannot prove this,
 *     since a bare {@code ListAppender} never runs a {@code PatternLayout} at all.</li>
 * </ol>
 */
public class MaskingMessageConverterTest {

    @Test(groups = "smoke")
    public void convertsARawUnmaskedSecretShapedMessage() {
        LoggingEvent event = buildEvent("Authorization: Bearer some-raw-unmasked-token");

        String converted = new MaskingMessageConverter().convert(event);

        assertFalse(converted.contains("some-raw-unmasked-token"), "The raw token must not survive conversion.");
        assertTrue(converted.contains(SensitiveDataMasker.MASK), "The masked placeholder must appear instead.");
    }

    @Test(groups = "smoke")
    public void realPatternLayoutEncoderMasksThroughTheFullPipeline() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Same conversionWord/pattern shape as logback.xml's <conversionRule>/LOG_PATTERN,
        // registered against the context here exactly the way logback.xml's own
        // <conversionRule> element does under the hood (PatternRuleAction populates this same
        // CoreConstants.PATTERN_RULE_REGISTRY map from the XML tag at configuration time).
        @SuppressWarnings("unchecked")
        Map<String, String> ruleRegistry = (Map<String, String>) context.getObject(CoreConstants.PATTERN_RULE_REGISTRY);
        if (ruleRegistry == null) {
            ruleRegistry = new HashMap<>();
            context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, ruleRegistry);
        }
        ruleRegistry.put("maskedMsg", MaskingMessageConverter.class.getName());

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%maskedMsg");
        encoder.start();

        LoggingEvent event = buildEvent("password=raw-unmasked-password-value");
        byte[] encoded = encoder.encode(event);
        String output = new String(encoded, StandardCharsets.UTF_8);

        assertFalse(output.contains("raw-unmasked-password-value"), "The real pipeline must not leak the raw password.");
        assertTrue(output.contains(SensitiveDataMasker.MASK), "The real pipeline must emit the mask placeholder instead.");
    }

    private static LoggingEvent buildEvent(String message) {
        Logger logger = (Logger) LoggerFactory.getLogger(MaskingMessageConverterTest.class);
        return new LoggingEvent(Logger.class.getName(), logger, Level.INFO, message, null, null);
    }
}
