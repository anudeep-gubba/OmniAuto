package com.tests.base;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Proves the CI hard-block for real, not by assumption: {@code -Dmasking.enabled=false} must
 * be silently ignored whenever a CI environment is detected, so it can never leak a secret into
 * a shared/CI report regardless of how the build was invoked. {@code System.getenv} has no
 * supported mutation API from inside a running JVM, so the only genuine way to prove this is to
 * spawn a real child JVM with a real {@code CI=true} environment variable - which is exactly
 * what this does, reusing the current test JVM's own classpath ({@code java.class.path}) so no
 * separate classpath assembly is needed.
 *
 * <p>Two runs, same flag, only the environment differs - proving both directions live:</p>
 * <ul>
 *     <li>{@link #withoutCiEnvironmentTheLocalEscapeHatchWorks()} - no {@code CI} variable:
 *     {@code -Dmasking.enabled=false} shows the real value, as designed for local debugging.</li>
 *     <li>{@link #withCiEnvironmentTheEscapeHatchIsIgnored()} - {@code CI=true} set: the exact
 *     same flag is ignored outright, masking stays on.</li>
 * </ul>
 */
public class MaskingCiHardBlockTest {

    @Test(groups = "smoke")
    public void withoutCiEnvironmentTheLocalEscapeHatchWorks() throws IOException, InterruptedException {
        String output = runSimulation(false);

        assertTrue(output.contains(MaskingCiSimulationMain.SECRET_VALUE),
                "Without a CI environment, -Dmasking.enabled=false must show the real value: " + output);
    }

    @Test(groups = "smoke")
    public void withCiEnvironmentTheEscapeHatchIsIgnored() throws IOException, InterruptedException {
        String output = runSimulation(true);

        assertFalse(output.contains(MaskingCiSimulationMain.SECRET_VALUE),
                "With CI=true set, -Dmasking.enabled=false must be ignored - the real value must never appear: " + output);
        assertTrue(output.contains("********"), "The value must still come out masked: " + output);
    }

    private static String runSimulation(boolean simulateCi) throws IOException, InterruptedException {
        String javaBinary = System.getProperty("java.home") + "/bin/java";
        List<String> command = List.of(
                javaBinary,
                "-cp", System.getProperty("java.class.path"),
                "-Dmasking.enabled=false",
                MaskingCiSimulationMain.class.getName());

        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        if (simulateCi) {
            processBuilder.environment().put("CI", "true");
        } else {
            // Strip inherited CI-ish variables so a run of this test *inside* GitHub Actions
            // itself (which sets these for its own process) doesn't accidentally simulate "CI"
            // for the "without CI" case too - this child process must genuinely look local.
            processBuilder.environment().remove("CI");
            processBuilder.environment().remove("GITHUB_ACTIONS");
        }

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(exitCode, 0, "MaskingCiSimulationMain must exit cleanly. Output: " + output);
        return output;
    }
}
