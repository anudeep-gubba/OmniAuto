package com.framework.reporting;

import com.aventstack.extentreports.ExtentTest;
import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The one seam that knows an artifact belongs in <em>both</em> reports, so callers
 * ({@link com.framework.listeners.ScreenshotCaptureListener} today) depend on a single
 * composite operation instead of importing {@link ExtentManager} and {@link AllureManager}
 * separately and remembering to call both every time (RULE 5).
 *
 * <p>Everything else - action-level step logging, API request/response text - is
 * intentionally <em>not</em> duplicated here: step logging reaches Extent automatically via
 * the Logback-to-Extent bridge ({@link ExtentLoggingAppender}, requirement.md &sect;18 - "the
 * test author should NOT need to manually add reporting code"), and Allure gets its own
 * fixture/result/retry data natively from {@code allure-testng}. A {@code ReportManager} that
 * wrapped those too would just be pass-through noise (requirement.md &sect;28).</p>
 *
 * <p><b>Also the single place that resolves {@link ConfigKeys#REPORT_TYPES}</b> - enterprise
 * finding: this framework's own reporting enrichment (Extent node/step creation, every Allure
 * attachment/step/masking call) has a real cost - formatting, masking, writing - that's wasted
 * work when nobody actually opens that report (this project's own CI, for instance, uploads
 * {@code allure-results/} as a build artifact but renders it nowhere). {@link #isExtentEnabled()}/
 * {@link #isAllureEnabled()} let every call site skip that work outright rather than doing it
 * and discarding the result. Resolved once, from {@link ConfigKeys#REPORT_TYPES} (default
 * {@code "extent"} only - the actively-used, fully-enriched report as of this framework's own
 * reporting work; {@code "allure"} is comparatively thin outside the API surface, see
 * README.md's Reporting section), not re-read per call - report type is a whole-run concern,
 * never a per-test override.</p>
 */
public final class ReportManager {

    private static final Set<String> ENABLED_TYPES = resolveEnabledTypes();

    private ReportManager() {
    }

    /** {@code true} unless {@link ConfigKeys#REPORT_TYPES} was set without {@code "extent"} in it. */
    public static boolean isExtentEnabled() {
        return ENABLED_TYPES.contains("extent");
    }

    /**
     * {@code true} only when {@code "allure"} is explicitly listed in {@link
     * ConfigKeys#REPORT_TYPES}. {@code allure-testng}'s own native pass/fail/{@code @Before}/
     * {@code @After} capture runs regardless of this value (see this class's javadoc) - this
     * only gates whether this framework's own code does any additional Allure enrichment work.
     */
    public static boolean isAllureEnabled() {
        return ENABLED_TYPES.contains("allure");
    }

    private static Set<String> resolveEnabledTypes() {
        String raw = ConfigManager.getString(ConfigKeys.REPORT_TYPES, "extent");
        Set<String> types = new HashSet<>();
        for (String type : raw.split(",")) {
            String trimmed = type.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                types.add(trimmed);
            }
        }
        return types;
    }

    /** Attaches {@code pngPath} to the calling thread's current Extent test node (if any) and to Allure (if enabled). */
    public static void attachScreenshot(Path pngPath, String label) {
        if (pngPath == null) {
            return;
        }
        ExtentTest test = ExtentManager.getTest();
        if (test != null) {
            test.addScreenCaptureFromPath(pngPath.toString(), label);
        }
        AllureManager.attachScreenshotFromPath(pngPath, label);
    }
}
