package com.tests.base;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.framework.api.services.AuthenticationService;
import com.framework.secrets.SecretManager;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Phase 10 validation: proves masking actually holds through the <em>real</em> SLF4J/Logback
 * pipeline during a genuine, live login - not just {@link com.framework.secrets.SensitiveDataMasker#mask}
 * in isolation, which {@code SensitiveDataMaskerTest} already covers thoroughly. Attaches a
 * capturing appender directly to {@code ApiClient}'s own named logger, matching this project's
 * standing preference for live proof over assumption (requirement.md &sect;16: "Sensitive
 * data MUST be masked").
 *
 * <p><b>Found in practice, not assumed:</b> the sanity check originally asserted the request's
 * email address <em>was</em> present in the captured output (proof the request was really
 * logged at all). That failed - not because logging was broken, but because
 * {@link SecretManager#get(String)} registers <em>every</em> value it resolves with
 * {@link com.framework.secrets.SensitiveDataMasker} by value, regardless of whether the key
 * name looks sensitive. Since {@code EVENTHUB_EMAIL} is itself stored as a secret (see
 * {@code eventhub-test-targets} project notes), the email is masked everywhere too, same as
 * the password - a real, correct, if non-obvious, consequence of "mask by known value"
 * covering more than just password-shaped fields. The sanity check now looks for the request
 * line and mask placeholder instead of the (also masked) email.</p>
 *
 * <p>Capturing at the {@code com.framework} logger, not just {@code ApiClient}'s own, is
 * deliberate: an earlier version of this test attached only to {@code ApiClient} and passed
 * while {@link AuthenticationService} was logging the raw email completely unmasked one class
 * away - masking is opt-in per call site (each one must call
 * {@link com.framework.secrets.SensitiveDataMasker#mask(String)} itself; nothing enforces it
 * framework-wide), so a regression test narrow enough to only watch the one class already
 * known to be careful proves nothing about the rest of the framework. Capturing at the
 * package level catches exactly that class of gap again if it recurs anywhere under
 * {@code com.framework}.</p>
 */
public class LoggingMaskingIntegrationTest {

    private final AuthenticationService authService = new AuthenticationService();

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        authService.logout();
    }

    @Test(groups = {"smoke", "api"})
    public void realLoginNeverLogsThePasswordThroughTheActualLogbackPipeline() {
        String email = SecretManager.get("EVENTHUB_EMAIL");
        String password = SecretManager.get("EVENTHUB_PASSWORD");

        Logger frameworkLogger = (Logger) LoggerFactory.getLogger("com.framework");
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        frameworkLogger.addAppender(capture);
        try {
            authService.login(email, password);
        } finally {
            frameworkLogger.detachAppender(capture);
        }

        String allOutput = capture.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);

        assertFalse(allOutput.contains(password), "The real password must never reach the log output.");
        assertFalse(allOutput.contains(email), "The email is itself a resolved secret, so it must be masked too.");
        assertTrue(allOutput.contains("POST /auth/login"), "Sanity check that the request was actually captured/logged.");
        assertTrue(allOutput.contains("********"), "Both the email and password should appear as the mask placeholder instead.");
    }
}
