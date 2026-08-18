package com.framework.utils;

import com.framework.exceptions.ConfigurationException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Case-insensitive, fail-fast enum lookup shared by every config-driven enum
 * ({@code Environment}, {@code BrowserType}, {@code MobilePlatformType}, ...),
 * so each one does not reimplement the same "parse and validate" logic
 * (RULE 5: do not create duplicate utilities).
 */
public final class EnumUtils {

    private EnumUtils() {
    }

    /**
     * @param enumType the enum class to resolve against
     * @param rawValue the raw configuration/CLI value, case-insensitive
     * @param cliFlag  how this value is normally set, for error messages (e.g. {@code "-Dbrowser"})
     */
    public static <E extends Enum<E>> E fromString(Class<E> enumType, String rawValue, String cliFlag) {
        E[] constants = enumType.getEnumConstants();
        String supported = joinLowercase(constants);
        if (rawValue == null || rawValue.isBlank()) {
            throw new ConfigurationException(
                    "No " + cliFlag + " value specified. Set one via " + cliFlag + "=<" + supported + ">.");
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(constants)
                .filter(constant -> constant.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException(
                        "Unsupported " + cliFlag + " value '" + rawValue + "'. Supported values: " + supported + "."));
    }

    private static <E extends Enum<E>> String joinLowercase(E[] constants) {
        StringBuilder builder = new StringBuilder();
        for (E constant : constants) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(constant.name().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }
}
