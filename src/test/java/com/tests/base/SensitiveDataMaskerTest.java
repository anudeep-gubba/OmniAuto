package com.tests.base;

import com.framework.secrets.SensitiveDataMasker;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Phase 3 validation: proves masking matches the literal examples in
 * requirement.md &sect;16 and does not touch non-sensitive fields.
 */
public class SensitiveDataMaskerTest {

    @Test(groups = "smoke")
    public void masksUnquotedKeyValuePairs() {
        assertEquals(SensitiveDataMasker.mask("password=mySecretPass123"), "password=********");
        assertEquals(SensitiveDataMasker.mask("client_secret=abc123"), "client_secret=********");
        assertEquals(SensitiveDataMasker.mask("access_token=abc123"), "access_token=********");
    }

    @Test(groups = "smoke")
    public void masksMultiWordHeaderValues() {
        assertEquals(SensitiveDataMasker.mask("Authorization: Bearer abc.def.ghi"), "Authorization: ********");
    }

    @Test(groups = "smoke")
    public void masksQuotedJsonStyleValuesPreservingQuotes() {
        assertEquals(
                SensitiveDataMasker.mask("{\"client_secret\": \"xyz789\", \"userId\": \"42\"}"),
                "{\"client_secret\": \"********\", \"userId\": \"42\"}");
    }

    @Test(groups = "smoke")
    public void stopsMaskedValueAtQueryStringSeparator() {
        assertEquals(SensitiveDataMasker.mask("access_token=abc123&foo=bar"), "access_token=********&foo=bar");
    }

    @Test(groups = "smoke")
    public void doesNotMaskNonSensitiveFields() {
        assertEquals(SensitiveDataMasker.mask("username=john, orderId=42"), "username=john, orderId=42");
    }

    @Test(groups = "smoke")
    public void masksAnyRegisteredLiteralSecretValueWherEverItAppears() {
        SensitiveDataMasker.registerSecretValue("unique-raw-secret-value-9f8e7d");
        assertEquals(
                SensitiveDataMasker.mask("response contained unique-raw-secret-value-9f8e7d in a field we did not expect"),
                "response contained ******** in a field we did not expect");
    }
}
