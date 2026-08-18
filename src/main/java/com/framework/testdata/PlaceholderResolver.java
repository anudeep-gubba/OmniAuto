package com.framework.testdata;

import com.framework.config.ConfigManager;
import com.framework.exceptions.SecretResolutionException;
import com.framework.secrets.SecretManager;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${{KEY}}} placeholders in arbitrary text against an ordered
 * list of {@link PlaceholderSource}s &mdash; the single variable-resolution
 * mechanism requirement.md's closing recommendation asks for, shared across
 * configuration, secrets, test data, and (from Phase 8) API chaining:
 *
 * <pre>
 * ${{LOGIN_USERNAME}}   -&gt; secret / CI environment value
 * ${{BASE_URL}}         -&gt; configuration value
 * ${{userId}}           -&gt; runtime / API context value (Phase 8)
 * </pre>
 *
 * <p>Built-in source order: secrets ({@link SecretManager}) first, then
 * configuration ({@link ConfigManager}, matched case-insensitively against
 * both the literal placeholder key and its {@code UPPER_SNAKE_CASE ->
 * dotted.lowercase} form, since {@code config/*.properties} keys use the
 * latter convention). A third source is added later via
 * {@link #registerSource(PlaceholderSource)} without any change here.</p>
 *
 * <p>Fail-fast: a token no source can resolve throws
 * {@link SecretResolutionException} rather than being left in the text or
 * silently replaced with an empty string (requirement.md &sect;14).</p>
 */
public final class PlaceholderResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{\\{\\s*([^}]+?)\\s*}}");

    private static final List<PlaceholderSource> SOURCES = new CopyOnWriteArrayList<>(List.of(
            key -> Optional.ofNullable(SecretManager.get(key, null)),
            key -> resolveFromConfig(key)
    ));

    private PlaceholderResolver() {
    }

    /** Registers an additional resolution source, checked after the built-in secret/config sources. */
    public static void registerSource(PlaceholderSource source) {
        SOURCES.add(source);
    }

    /** Substitutes every {@code ${{KEY}}} token in {@code rawText}. Text with no placeholders is returned unchanged. */
    public static String resolve(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return rawText;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawText);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolveKey(key)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveKey(String key) {
        for (PlaceholderSource source : SOURCES) {
            Optional<String> value = source.resolve(key);
            if (value.isPresent()) {
                return value.get();
            }
        }
        throw new SecretResolutionException(
                "Unresolved placeholder '${{" + key + "}}'. Checked secrets (.secret.env / CI environment "
                        + "variables), configuration (config/*.properties), and any registered runtime "
                        + "sources. Define it in one of those.");
    }

    private static Optional<String> resolveFromConfig(String key) {
        String direct = ConfigManager.getString(key, null);
        if (direct != null) {
            return Optional.of(direct);
        }
        return Optional.ofNullable(ConfigManager.getString(toPropertiesStyleKey(key), null));
    }

    private static String toPropertiesStyleKey(String placeholderKey) {
        return placeholderKey.toLowerCase(Locale.ROOT).replace('_', '.');
    }
}
