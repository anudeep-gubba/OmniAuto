package com.framework.enums;

import com.framework.utils.EnumUtils;

/**
 * Configurable screenshot capture modes (requirement.md &sect;19). Resolved
 * from the {@code screenshot.mode} configuration key.
 */
public enum ScreenshotMode {
    FAILURE, EVERY_ACTION, DISABLED;

    public static ScreenshotMode fromString(String rawValue) {
        return EnumUtils.fromString(ScreenshotMode.class, rawValue, "-Dscreenshot.mode");
    }
}
