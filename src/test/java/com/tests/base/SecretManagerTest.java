package com.tests.base;

import com.framework.exceptions.SecretResolutionException;
import com.framework.secrets.SecretManager;
import com.framework.secrets.SensitiveDataMasker;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 3 validation: proves {@code .secret.env} resolution, fail-fast on a
 * missing secret, and automatic mask registration.
 *
 * <p>CI-environment-variable precedence (tier 1 beating {@code .secret.env})
 * is not exercised here: {@code System.getenv} cannot be mutated from within
 * a running JVM, so that tier is proved by an ad hoc
 * {@code KEY=value mvn test} invocation instead (see Phase 3 summary) rather
 * than a permanent test that would need external environment coordination.</p>
 */
public class SecretManagerTest {

    @Test(groups = "smoke")
    public void resolvesFromLocalSecretEnvFile() {
        assertEquals(SecretManager.get("LOGIN_USERNAME"), "testuser");
        assertEquals(SecretManager.get("LOGIN_PASSWORD"), "secretPassword");
    }

    @Test(groups = "smoke")
    public void hasReflectsPresenceWithoutThrowing() {
        assertTrue(SecretManager.has("LOGIN_USERNAME"));
        assertFalse(SecretManager.has("DEFINITELY_NOT_A_REAL_SECRET_KEY"));
    }

    @Test(groups = "smoke")
    public void missingSecretFailsFast() {
        SecretResolutionException exception = expectThrows(SecretResolutionException.class,
                () -> SecretManager.get("DEFINITELY_NOT_A_REAL_SECRET_KEY"));
        assertTrue(exception.getMessage().contains("DEFINITELY_NOT_A_REAL_SECRET_KEY"));
    }

    @Test(groups = "smoke")
    public void defaultValueOverloadReturnsFallbackInsteadOfThrowing() {
        assertEquals(SecretManager.get("DEFINITELY_NOT_A_REAL_SECRET_KEY", "fallback"), "fallback");
    }

    @Test(groups = "smoke")
    public void resolvedSecretIsAutomaticallyMaskable() {
        String password = SecretManager.get("LOGIN_PASSWORD");
        String logLine = "attempting login with password " + password;
        assertTrue(SensitiveDataMasker.mask(logLine).contains(SensitiveDataMasker.MASK));
        assertFalse(SensitiveDataMasker.mask(logLine).contains(password));
    }
}
