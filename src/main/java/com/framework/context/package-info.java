/**
 * Thread-safe runtime variable storage for API/data chaining.
 *
 * <p>{@link com.framework.context.VariableManager} (Phase 8) is the shared,
 * general-purpose store; {@link com.framework.api.ApiContext} is the
 * API-facing surface built on top of it. A broader test-scoped
 * {@code TestContext} (test name, environment, browser, device, platform for
 * reporting - requirement.md &sect;17) is deferred to Phase 11, where those
 * fields are actually consumed.</p>
 */
package com.framework.context;
