package com.framework.enums;

import com.framework.utils.EnumUtils;

/**
 * Supported Web browsers (requirement.md &sect;25). Resolved from the
 * {@code browser} configuration key (e.g. {@code -Dbrowser=chrome}); an
 * unrecognized value fails fast via {@link #fromString(String)}.
 */
public enum BrowserType {
    CHROME, FIREFOX, EDGE, SAFARI;

    public static BrowserType fromString(String rawValue) {
        return EnumUtils.fromString(BrowserType.class, rawValue, "-Dbrowser");
    }
}
