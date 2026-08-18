package com.framework.reporting;

import com.aventstack.extentreports.ExtentTest;

import java.nio.file.Path;

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
 */
public final class ReportManager {

    private ReportManager() {
    }

    /** Attaches {@code pngPath} to the calling thread's current Extent test node (if any) and to Allure. */
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
