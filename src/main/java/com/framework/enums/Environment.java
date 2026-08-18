package com.framework.enums;

import com.framework.utils.EnumUtils;

/**
 * Supported execution environments (requirement.md &sect;12).
 *
 * <p>Resolved from the {@code env} system property (e.g. {@code -Denv=dev}),
 * defaulting to {@link #QA} when omitted (see {@link com.framework.config.ConfigManager}).
 * An unrecognized value fails fast via {@link #fromString(String)} rather than silently
 * falling back.</p>
 *
 * <p>Only {@code QA}/{@code DEV} for now - {@code UAT}/{@code STAGING} were removed
 * (config/uat.properties, config/staging.properties deleted) to keep the environment set to
 * what's actually exercised, rather than placeholder files nothing points at differently.
 * Add a new constant here plus its matching {@code config/{env}.properties} file to bring
 * one back.</p>
 */
public enum Environment {
    QA, DEV;

    public static Environment fromString(String rawValue) {
        return EnumUtils.fromString(Environment.class, rawValue, "-Denv");
    }
}
