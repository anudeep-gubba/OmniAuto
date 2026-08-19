package com.tests.base;

import com.framework.secrets.SensitiveDataMasker;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Phase 3 validation: proves masking matches the literal examples in
 * requirement.md &sect;16 and does not touch non-sensitive fields.
 *
 * <p>Output is {@code ********-xxxxxxxx} (a deterministic fingerprint suffix), not a flat
 * {@code ********} - see {@link SensitiveDataMasker}'s class javadoc for why (real-world
 * debugging finding: a flat mask gave no way to tell whether a failed test used the expected
 * credential or a wrong one). Assertions here check the raw value is gone and the mask prefix
 * is present, rather than an exact masked string, since the fingerprint suffix is
 * value-dependent by design.</p>
 */
public class SensitiveDataMaskerTest {

    @Test(groups = "smoke")
    public void masksUnquotedKeyValuePairs() {
        assertMasked(SensitiveDataMasker.mask("password=mySecretPass123"), "password=", "mySecretPass123");
        assertMasked(SensitiveDataMasker.mask("client_secret=abc123"), "client_secret=", "abc123");
        assertMasked(SensitiveDataMasker.mask("access_token=abc123"), "access_token=", "abc123");
    }

    @Test(groups = "smoke")
    public void masksMultiWordHeaderValues() {
        assertMasked(SensitiveDataMasker.mask("Authorization: Bearer abc.def.ghi"), "Authorization: ", "Bearer abc.def.ghi");
    }

    @Test(groups = "smoke")
    public void masksQuotedJsonStyleValuesPreservingQuotes() {
        String masked = SensitiveDataMasker.mask("{\"client_secret\": \"xyz789\", \"userId\": \"42\"}");
        assertFalse(masked.contains("xyz789"), "The raw secret value must not survive masking.");
        assertTrue(masked.contains("\"userId\": \"42\""), "A non-sensitive field must be untouched.");
        assertTrue(masked.contains("\"client_secret\": \"" + SensitiveDataMasker.MASK),
                "The client_secret value must be replaced with the mask prefix, quotes preserved: " + masked);
    }

    @Test(groups = "smoke")
    public void stopsMaskedValueAtQueryStringSeparator() {
        String masked = SensitiveDataMasker.mask("access_token=abc123&foo=bar");
        assertFalse(masked.contains("abc123"), "The raw token value must not survive masking.");
        assertTrue(masked.endsWith("&foo=bar"), "Text after the separator must be untouched.");
        assertTrue(masked.startsWith("access_token=" + SensitiveDataMasker.MASK));
    }

    @Test(groups = "smoke")
    public void doesNotMaskNonSensitiveFields() {
        assertEquals(SensitiveDataMasker.mask("username=john, orderId=42"), "username=john, orderId=42");
    }

    @Test(groups = "smoke")
    public void masksAnyRegisteredLiteralSecretValueWherEverItAppears() {
        SensitiveDataMasker.registerSecretValue("unique-raw-secret-value-9f8e7d");
        String masked = SensitiveDataMasker.mask("response contained unique-raw-secret-value-9f8e7d in a field we did not expect");
        assertFalse(masked.contains("unique-raw-secret-value-9f8e7d"), "The raw value must not survive masking.");
        assertTrue(masked.contains(SensitiveDataMasker.MASK), "The mask prefix must appear in its place.");
    }

    /**
     * The entire point of fingerprinting: without ever exposing the real value, a report
     * reader can tell two occurrences of the *same* secret apart from two *different* secrets
     * purely by comparing masked output - e.g. "was the same password entered in both attempts,
     * or a different (wrong) one".
     */
    @Test(groups = "smoke")
    public void sameSecretValueAlwaysProducesTheSameMaskedFingerprint() {
        SensitiveDataMasker.registerSecretValue("repeatable-secret-value-12345");
        String first = SensitiveDataMasker.mask("attempt 1: repeatable-secret-value-12345");
        String second = SensitiveDataMasker.mask("attempt 2: repeatable-secret-value-12345");

        String firstMasked = first.substring(first.indexOf(SensitiveDataMasker.MASK));
        String secondMasked = second.substring(second.indexOf(SensitiveDataMasker.MASK));
        assertEquals(firstMasked, secondMasked, "The same secret value must always mask to the same fingerprint.");
    }

    @Test(groups = "smoke")
    public void differentSecretValuesProduceDifferentMaskedFingerprints() {
        SensitiveDataMasker.registerSecretValue("secret-value-alpha-000001");
        SensitiveDataMasker.registerSecretValue("secret-value-beta-000002");
        String maskedAlpha = SensitiveDataMasker.mask("secret-value-alpha-000001");
        String maskedBeta = SensitiveDataMasker.mask("secret-value-beta-000002");

        assertNotEquals(maskedAlpha, maskedBeta, "Different secret values must not collide onto the same masked output.");
    }

    /**
     * The local debugging escape hatch. Mutates a process-wide {@code System} property, not a
     * thread-local - safe under this project's normal sequential test execution, but this test
     * must never run concurrently with another masking assertion under {@code -Dparallel=methods}
     * sharing the same JVM (this codebase's test suite does not run that way by default - see
     * README's Thread safety section for the same caveat applied elsewhere). Always restores the
     * property in {@code finally} so a failure here can't leave masking disabled for every test
     * that runs after it in the same JVM.
     *
     * <p>The CI hard-block itself (this flag must be ignored entirely whenever a CI environment
     * is detected) is proven separately in {@code MaskingCiHardBlockTest}, which spawns a real
     * child JVM with a real {@code CI=true} environment variable - not fakeable from inside this
     * same process, since {@code System.getenv} has no supported mutation API.</p>
     */
    @Test(groups = "smoke")
    public void localEscapeHatchDisablesMaskingWhenExplicitlySet() {
        SensitiveDataMasker.registerSecretValue("locally-visible-secret-999888");
        System.setProperty("masking.enabled", "false");
        try {
            String output = SensitiveDataMasker.mask("value: locally-visible-secret-999888");
            assertTrue(output.contains("locally-visible-secret-999888"),
                    "-Dmasking.enabled=false must show the real value for local debugging.");
        } finally {
            System.clearProperty("masking.enabled");
        }

        String outputAfterReset = SensitiveDataMasker.mask("value: locally-visible-secret-999888");
        assertFalse(outputAfterReset.contains("locally-visible-secret-999888"),
                "Masking must default back to enabled once the property is cleared.");
    }

    private static void assertMasked(String maskedOutput, String expectedPrefix, String rawValue) {
        assertFalse(maskedOutput.contains(rawValue), "The raw value must not survive masking: " + maskedOutput);
        assertTrue(maskedOutput.startsWith(expectedPrefix + SensitiveDataMasker.MASK),
                "Expected '" + expectedPrefix + SensitiveDataMasker.MASK + "...' but got: " + maskedOutput);
    }
}
