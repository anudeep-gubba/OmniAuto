package com.framework.utils;

import java.util.UUID;

/**
 * Unique test-data generation (requirement.md &sect;4's target {@code utils/RandomDataUtils}).
 * Centralizes what several test classes previously did inline with their own
 * {@code UUID.randomUUID()} call (RULE 5 - no duplicated logic), e.g. a fresh registration
 * email that must not collide with a real account, or a probe value used to prove
 * thread-isolation by being unique per invocation.
 */
public final class RandomDataUtils {

    private RandomDataUtils() {
    }

    /** A unique email address, e.g. {@code uniqueEmail("framework.test")} -&gt; {@code "framework.test.<uuid>@example.com"}. */
    public static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.com";
    }

    /** A unique opaque token, for anything that just needs to be different every time it's generated. */
    public static String uniqueId() {
        return UUID.randomUUID().toString();
    }
}
