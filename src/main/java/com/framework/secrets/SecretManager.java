package com.framework.secrets;

import com.framework.exceptions.SecretResolutionException;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves secret values through the 2-tier precedence chain from
 * requirement.md &sect;13 (highest wins):
 * <ol>
 *     <li>CI/CD environment variable &mdash; {@code System.getenv(key)}</li>
 *     <li>Local {@code .secret.env} file (git-ignored; developer machines only,
 *     see {@code .secret.env.example} for the expected format)</li>
 * </ol>
 *
 * <p>Every value this class returns is registered with {@link SensitiveDataMasker}
 * automatically, so it is redacted everywhere the framework logs or reports text
 * from then on &mdash; callers never need to remember to mask it themselves
 * (RULE 7: do not log secrets).</p>
 *
 * <p><b>Thread-safety:</b> {@code .secret.env} is parsed once (double-checked
 * lazy init) and then treated as immutable/globally shareable &mdash; the same
 * classification as {@link com.framework.config.ConfigManager}'s global tier
 * (requirement.md &sect;21, category 1).</p>
 */
public final class SecretManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretManager.class);

    private static volatile Dotenv dotenv;
    private static final Object INIT_LOCK = new Object();

    private SecretManager() {
    }

    public static String get(String key) {
        String value = resolve(key);
        if (value == null) {
            throw new SecretResolutionException(
                    "Missing required secret '" + key + "'. Checked a CI/CD environment variable named '"
                            + key + "' and the '" + key + "' entry in .secret.env. Set the environment "
                            + "variable in CI, or add it to .secret.env locally (see .secret.env.example).");
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        String value = resolve(key);
        return value != null ? value : defaultValue;
    }

    public static boolean has(String key) {
        return resolve(key) != null;
    }

    private static String resolve(String key) {
        String ciValue = System.getenv(key);
        String value = ciValue != null ? ciValue : dotenv().get(key);
        if (value != null) {
            SensitiveDataMasker.registerSecretValue(value);
        }
        return value;
    }

    private static Dotenv dotenv() {
        Dotenv result = dotenv;
        if (result == null) {
            synchronized (INIT_LOCK) {
                result = dotenv;
                if (result == null) {
                    result = loadDotenv();
                    dotenv = result;
                }
            }
        }
        return result;
    }

    private static Dotenv loadDotenv() {
        try {
            Dotenv loaded = Dotenv.configure()
                    .directory(".")
                    .filename(".secret.env")
                    .ignoreIfMissing()
                    .load();
            if (loaded.entries().isEmpty()) {
                LOGGER.info(".secret.env not found or empty; relying on CI/CD environment variables only.");
            } else {
                LOGGER.info(".secret.env loaded for local secret resolution "
                        + "(CI/CD environment variables still take precedence over it).");
            }
            return loaded;
        } catch (DotenvException e) {
            throw new SecretResolutionException("Failed to parse .secret.env.", e);
        }
    }
}
