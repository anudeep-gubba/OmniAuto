package com.tests.base;

import com.framework.exceptions.SecretResolutionException;
import com.framework.testdata.PlaceholderResolver;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 3 validation: proves {@code ${{KEY}}} substitution against both
 * built-in sources (secrets, config) and against the {@link com.framework.testdata.PlaceholderSource}
 * extension point future phases (e.g. Phase 8's runtime/API context) will use.
 */
public class PlaceholderResolverTest {

    @Test(groups = "smoke")
    public void resolvesSecretPlaceholder() {
        assertEquals(PlaceholderResolver.resolve("${{LOGIN_USERNAME}}"), "testuser");
    }

    @Test(groups = "smoke")
    public void resolvesConfigPlaceholderAcrossNamingConventions() {
        // Placeholder uses UPPER_SNAKE_CASE; config/*.properties uses dotted.lowercase.
        // PlaceholderResolver bridges the two conventions (see its Javadoc).
        assertEquals(PlaceholderResolver.resolve("${{BASE_URL}}"), "https://eventhub.rahulshettyacademy.com");
    }

    @Test(groups = "smoke")
    public void resolvesMultiplePlaceholdersInOneDocument() {
        String template = "{\"username\":\"${{LOGIN_USERNAME}}\",\"password\":\"${{LOGIN_PASSWORD}}\"}";
        String resolved = PlaceholderResolver.resolve(template);
        assertEquals(resolved, "{\"username\":\"testuser\",\"password\":\"secretPassword\"}");
    }

    @Test(groups = "smoke")
    public void textWithoutPlaceholdersIsReturnedUnchanged() {
        assertEquals(PlaceholderResolver.resolve("no placeholders here"), "no placeholders here");
    }

    @Test(groups = "smoke")
    public void unresolvedPlaceholderFailsFast() {
        SecretResolutionException exception = expectThrows(SecretResolutionException.class,
                () -> PlaceholderResolver.resolve("${{NOT_A_REAL_KEY_ANYWHERE}}"));
        assertTrue(exception.getMessage().contains("NOT_A_REAL_KEY_ANYWHERE"));
    }

    @Test(groups = "smoke")
    public void customRegisteredSourceIsConsultedAfterBuiltInOnes() {
        // Simulates Phase 8 registering a runtime/API-context source without this
        // class changing: ${{userId}} style values.
        PlaceholderResolver.registerSource(key ->
                "userId".equals(key) ? Optional.of("42") : Optional.empty());

        assertEquals(PlaceholderResolver.resolve("${{userId}}"), "42");
    }
}
