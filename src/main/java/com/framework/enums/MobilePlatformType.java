package com.framework.enums;

import com.framework.utils.EnumUtils;

/**
 * Supported mobile platforms (requirement.md &sect;8). Resolved from the
 * {@code mobile.platform} configuration key; an unrecognized value fails
 * fast via {@link #fromString(String)}.
 */
public enum MobilePlatformType {
    ANDROID, IOS;

    public static MobilePlatformType fromString(String rawValue) {
        return EnumUtils.fromString(MobilePlatformType.class, rawValue, "-Dmobile.platform");
    }
}
