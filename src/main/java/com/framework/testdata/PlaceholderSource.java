package com.framework.testdata;

import java.util.Optional;

/**
 * A single source {@link PlaceholderResolver} can query when substituting a
 * {@code ${{KEY}}} token. Built-in sources wrap
 * {@link com.framework.secrets.SecretManager} and
 * {@link com.framework.config.ConfigManager}; the runtime/API-context layer
 * (requirement.md &sect;11, Phase 8) registers a third source for
 * {@code ${{userId}}}-style values without this interface or the resolver
 * needing to change.
 */
@FunctionalInterface
public interface PlaceholderSource {

    Optional<String> resolve(String key);
}
