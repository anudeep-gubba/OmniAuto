package com.framework.secrets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * <p><b>Fingerprinted, not flat, output</b> (real-world finding: a flat {@code ********}
 * everywhere made debugging a failed login unnecessarily hard &mdash; there is no way to
 * tell from a report whether the right dataset row/credential was actually used). Every
 * masked value now becomes {@code ********-xxxxxxxx}, where the suffix is a short,
 * deterministic fingerprint ({@link #fingerprint(String)}) of the real value: the same
 * secret always produces the same suffix, and different secrets (almost certainly) produce
 * different suffixes, so a report reader can tell "was this the same email as three steps
 * ago" or "did this test run against a different dataset row than that one" purely by
 * comparing suffixes &mdash; without the real value ever appearing anywhere shared. The
 * fingerprint is one-way (SHA-256) and deliberately short; it is a debugging aid for
 * correlation, not a cryptographic commitment, and must never be treated as safe against a
 * determined attacker with a small guess-list (e.g. a 4-digit PIN) hashing candidates to
 * find a match.</p>
 *
 * <p><b>Local-only escape hatch:</b> {@code -Dmasking.enabled=false} disables masking
 * entirely (returns input unchanged) &mdash; for a developer debugging locally, where the
 * real secret already sits in their own {@code .secret.env}, masking protects nobody.
 * Hard-blocked whenever a CI environment is detected ({@code CI}/{@code GITHUB_ACTIONS}
 * environment variables, set automatically by GitHub Actions and most other CI providers):
 * the flag is silently ignored in that case, masking always stays on, so this can never
 * leak into a shared/CI report regardless of how the build was invoked. Logs a one-time
 * warning to stderr (not through SLF4J/Logback, deliberately &mdash; see
 * {@link #warnOnceIfSuppressed()}) whenever it actually takes effect, so disabled masking is
 * never silent.</p>
 */
public final class SensitiveDataMasker {

    public static final String MASK = "********";

    private static final String MASKING_ENABLED_PROPERTY = "masking.enabled";

    private static final String KEY_NAME_ALTERNATION = String.join("|",
            "password", "pwd", "secret", "token", "authorization", "auth",
            "api[_-]?key", "client[_-]?secret", "access[_-]?token",
            "refresh[_-]?token", "session[_-]?id");

    private static final Pattern QUOTED_JSON_STYLE = Pattern.compile(
            "(?i)(\"(?:" + KEY_NAME_ALTERNATION + ")\"\\s*:\\s*\")([^\"]*)(\")");

    private static final Pattern UNQUOTED_KEY_VALUE_STYLE = Pattern.compile(
            "(?i)\\b(?:" + KEY_NAME_ALTERNATION + ")\\b\\s*[:=]\\s*(?!\")(\\S.*?)(?=[,;&\\n]|$)");

    private static final Set<String> KNOWN_SECRET_VALUES = ConcurrentHashMap.newKeySet();

    private static final AtomicBoolean SUPPRESSION_WARNED = new AtomicBoolean(false);

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
        if (isMaskingSuppressed()) {
            return input;
        }
        String result = input;
        for (String secretValue : KNOWN_SECRET_VALUES) {
            result = result.replace(secretValue, maskToken(secretValue));
        }
        result = maskQuotedJsonStyle(result);
        result = maskUnquotedKeyValueStyle(result);
        return result;
    }

    private static String maskQuotedJsonStyle(String input) {
        Matcher matcher = QUOTED_JSON_STYLE.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(matcher.group(1) + maskToken(matcher.group(2)) + matcher.group(3)));
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
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + maskToken(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** {@code ********} plus a short deterministic fingerprint of {@code rawValue} — see the class javadoc. */
    private static String maskToken(String rawValue) {
        return MASK + "-" + fingerprint(rawValue);
    }

    /** First 4 bytes (8 hex chars) of {@code value}'s SHA-256 digest. A correlation aid, not a security boundary. */
    private static String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JDK-mandatory algorithm (JLS/JCA spec) on every conforming JVM.
            throw new IllegalStateException("SHA-256 MessageDigest unavailable - not a conforming JVM.", e);
        }
    }

    /**
     * {@code true} only for a deliberate local {@code -Dmasking.enabled=false}, and never when
     * a CI environment is detected, regardless of that flag - see the class javadoc.
     */
    private static boolean isMaskingSuppressed() {
        if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null) {
            return false;
        }
        boolean suppressed = "false".equalsIgnoreCase(System.getProperty(MASKING_ENABLED_PROPERTY, "true"));
        if (suppressed) {
            warnOnceIfSuppressed();
        }
        return suppressed;
    }

    /**
     * Printed directly to stderr rather than through SLF4J/Logback deliberately: this can fire
     * from the very first {@link #mask(String)} call in the entire run (e.g. inside
     * {@code com.framework.secrets.MaskingMessageConverter}, itself invoked while rendering a log
     * event), before assuming anything about Logback's own configuration state is safe. A raw
     * {@code System.err} write has no such dependency.
     */
    private static void warnOnceIfSuppressed() {
        if (SUPPRESSION_WARNED.compareAndSet(false, true)) {
            System.err.println("[SensitiveDataMasker] Masking is DISABLED (-D" + MASKING_ENABLED_PROPERTY
                    + "=false): real secret values will appear in logs/reports for this run. "
                    + "Local debugging only - never valid in CI, and this flag is ignored there automatically.");
        }
    }
}
