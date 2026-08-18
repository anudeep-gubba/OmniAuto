package com.framework.secrets;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive values out of arbitrary text before it reaches a log line or
 * a report (requirement.md &sect;16, &sect;19; RULE 7/19).
 *
 * <p>Two independent masking strategies run on every call, in order:</p>
 * <ol>
 *     <li><b>Known-value masking</b> &mdash; any literal value ever registered via
 *     {@link #registerSecretValue(String)} is replaced wherever it appears
 *     verbatim, regardless of surrounding context. {@link SecretManager}
 *     registers every value it resolves automatically, so a secret is
 *     maskable everywhere the moment it is first read.</li>
 *     <li><b>Key-pattern masking</b> &mdash; text shaped like {@code password=...},
 *     {@code "client_secret": "..."} or {@code Authorization: Bearer ...} is
 *     masked by key name even when the value was never explicitly
 *     registered (e.g. a field in an API response the test never asked
 *     SecretManager for).</li>
 * </ol>
 *
 * <p><b>Design trade-off:</b> the key-pattern regex stops a captured value at
 * a comma, semicolon, {@code &} or newline &mdash; not at whitespace &mdash; so that
 * multi-word values such as {@code Authorization: Bearer <token>} are masked
 * in full. This means an unusual space-separated {@code key=value key2=value2}
 * log format may over-mask past the first field. That is a deliberate choice:
 * over-masking is safe, leaking part of a secret is not.</p>
 */
public final class SensitiveDataMasker {

    public static final String MASK = "********";

    private static final String KEY_NAME_ALTERNATION = String.join("|",
            "password", "pwd", "secret", "token", "authorization", "auth",
            "api[_-]?key", "client[_-]?secret", "access[_-]?token",
            "refresh[_-]?token", "session[_-]?id");

    private static final Pattern QUOTED_JSON_STYLE = Pattern.compile(
            "(?i)(\"(?:" + KEY_NAME_ALTERNATION + ")\"\\s*:\\s*\")([^\"]*)(\")");

    private static final Pattern UNQUOTED_KEY_VALUE_STYLE = Pattern.compile(
            "(?i)\\b(?:" + KEY_NAME_ALTERNATION + ")\\b\\s*[:=]\\s*(?!\")(\\S.*?)(?=[,;&\\n]|$)");

    private static final Set<String> KNOWN_SECRET_VALUES = ConcurrentHashMap.newKeySet();

    private SensitiveDataMasker() {
    }

    /**
     * Registers a literal secret value to always mask, wherever it appears in text
     * passed to {@link #mask(String)}. Values of length &le; 3 are ignored to avoid
     * mass false-positive masking on short, low-entropy strings.
     */
    public static void registerSecretValue(String value) {
        if (value != null && value.length() > 3) {
            KNOWN_SECRET_VALUES.add(value);
        }
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (String secretValue : KNOWN_SECRET_VALUES) {
            result = result.replace(secretValue, MASK);
        }
        result = maskQuotedJsonStyle(result);
        result = maskUnquotedKeyValueStyle(result);
        return result;
    }

    private static String maskQuotedJsonStyle(String input) {
        Matcher matcher = QUOTED_JSON_STYLE.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + MASK + matcher.group(3)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String maskUnquotedKeyValueStyle(String input) {
        Matcher matcher = UNQUOTED_KEY_VALUE_STYLE.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String whole = matcher.group();
            String value = matcher.group(1);
            String prefix = whole.substring(0, whole.length() - value.length());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + MASK));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
