package com.framework.enums;

import com.framework.utils.EnumUtils;

/**
 * Where a mobile test's device comes from (requirement.md &sect;34 - cloud device farm
 * extensibility). Resolved from the {@code mobile.device.provider} configuration key,
 * defaulting to {@link #LOCAL} when unset so existing local-emulator/local-device setups
 * (see {@code config/dev.properties}) need no change.
 *
 * <p>{@link #LOCAL} covers both an emulator/simulator <em>and</em> a physical device plugged
 * into this machine - both talk to the same local Appium server, differing only in
 * {@code mobile.udid} (a real device's serial vs. an emulator's AVD-derived name); there is
 * no separate enum value for "physical device" because nothing about capability-building or
 * the server URL actually differs between the two.</p>
 */
public enum MobileDeviceProvider {
    LOCAL, BROWSERSTACK;

    public static MobileDeviceProvider fromString(String rawValue) {
        return EnumUtils.fromString(MobileDeviceProvider.class, rawValue, "-Dmobile.device.provider");
    }
}
