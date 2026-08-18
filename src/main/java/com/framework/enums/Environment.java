package com.framework.enums;

import com.framework.utils.EnumUtils;

/**
 * Supported execution environments (requirement.md &sect;12).
 *
 * <p>Resolved from the {@code env} system property (e.g. {@code -Denv=uat}),
 * defaulting to {@link #QA} when omitted (see {@link com.framework.config.ConfigManager}).
 * An unrecognized value fails fast via {@link #fromString(String)} rather than silently
 * falling back.</p>
 */
public enum Environment {
    QA, DEV, UAT, STAGING;

    public static Environment fromString(String rawValue) {
        return EnumUtils.fromString(Environment.class, rawValue, "-Denv");
    }
}
