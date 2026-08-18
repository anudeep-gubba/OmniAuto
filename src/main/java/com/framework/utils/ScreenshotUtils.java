package com.framework.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Captures a screenshot from any driver that supports it - both
 * {@link org.openqa.selenium.WebDriver} and
 * {@link io.appium.java_client.AppiumDriver} implement
 * {@link TakesScreenshot}, so this one utility serves Web (Phase 5) and
 * Mobile (Phase 6) alike, matching {@code utils/ScreenshotUtils} in
 * requirement.md &sect;4.
 *
 * <p>Best-effort: a capture failure logs a warning and returns {@code null}
 * rather than throwing, since a screenshot is diagnostic, not the thing
 * under test.</p>
 */
public final class ScreenshotUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenshotUtils.class);
    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private ScreenshotUtils() {
    }

    /**
     * Saves a PNG screenshot under {@code target/screenshots/} and returns its path,
     * or {@code null} if the driver does not support screenshots or capture fails.
     */
    public static Path capture(WebDriver driver, String label) {
        if (!(driver instanceof TakesScreenshot takesScreenshot)) {
            LOGGER.warn("Driver does not support screenshots; skipping capture for '{}'.", label);
            return null;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            File source = takesScreenshot.getScreenshotAs(OutputType.FILE);
            String fileName = sanitize(label) + "-" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".png";
            Path destination = SCREENSHOT_DIR.resolve(fileName);
            Files.copy(source.toPath(), destination);
            LOGGER.info("Screenshot captured: {}", destination);
            return destination;
        } catch (IOException e) {
            LOGGER.warn("Failed to capture screenshot for '{}': {}", label, e.getMessage());
            return null;
        }
    }

    private static String sanitize(String label) {
        return label.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
