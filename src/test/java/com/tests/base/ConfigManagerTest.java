package com.tests.base;

import com.framework.config.ConfigManager;
import com.framework.exceptions.ConfigurationException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 2 validation: proves the 5-tier configuration precedence chain and
 * fail-fast validation described in {@link ConfigManager}.
 *
 * <p>Multi-environment file <em>selection</em> (dev/qa.properties)
 * is validated separately by running {@code mvn test -Denv=<env>} for each
 * value (see Phase 2 summary) rather than here: the merged config is cached
 * for the whole process by design, so a single JVM only ever resolves one
 * environment.</p>
 */
public class ConfigManagerTest {

    @AfterMethod(alwaysRun = true)
    public void cleanupThreadState() {
        ConfigManager.clearThreadState();
    }

    @Test(groups = "smoke")
    public void environmentResolvesToASupportedValue() {
        assertNotNull(ConfigManager.getEnvironment());
    }

    @Test(groups = "smoke")
    public void activeEnvironmentPointsAtSandboxTargets() {
        // Every shipped environment file points at the same public sandboxes today
        // (see config/*.properties comments), so this holds regardless of which
        // environment this particular JVM run resolved to.
        assertEquals(ConfigManager.getBaseUrl(), "https://eventhub.rahulshettyacademy.com");
        assertEquals(ConfigManager.getApiBaseUrl(), "https://api.eventhub.rahulshettyacademy.com/api");
    }

    @Test(groups = "smoke")
    public void browserAndHeadlessDefaultsAreReadable() {
        assertFalse(ConfigManager.getBrowser().isBlank());
        // No literal-value assertion: a real invocation may pass -Dbrowser=.../-Dheadless=...
        ConfigManager.isHeadless();
    }

    @Test(groups = "smoke")
    public void testSpecificOverrideWinsOverEverythingElseAndClearsCleanly() {
        String originalBrowser = ConfigManager.getBrowser();

        ConfigManager.setOverride("browser", "firefox-override-test");
        assertEquals(ConfigManager.getString("browser"), "firefox-override-test");

        ConfigManager.clearThreadState();
        assertEquals(ConfigManager.getBrowser(), originalBrowser);
    }

    @Test(groups = "smoke")
    public void testNgParameterTierOutranksGlobalConfigButNotOverride() {
        ConfigManager.setTestNgParameters(Map.of("browser", "edge-from-parameter"));
        assertEquals(ConfigManager.getString("browser"), "edge-from-parameter");

        ConfigManager.setOverride("browser", "override-wins");
        assertEquals(ConfigManager.getString("browser"), "override-wins");
    }

    @Test(groups = "smoke")
    public void missingRequiredKeyFailsFast() {
        ConfigurationException exception = expectThrows(ConfigurationException.class,
                () -> ConfigManager.getString("this.key.does.not.exist.anywhere"));
        assertTrue(exception.getMessage().contains("this.key.does.not.exist.anywhere"));
    }
}
