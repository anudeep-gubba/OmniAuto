package com.framework.constants;

/**
 * Canonical configuration key names, shared by {@code config/*.properties} files,
 * {@code -Dkey=value} system-property overrides, and TestNG {@code <parameter>} tags.
 * Keeping these as constants (rather than repeating string literals) prevents key-name
 * typos from silently creating a new, unresolved config entry.
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    public static final String ENV = "env";
    public static final String BROWSER = "browser";
    public static final String HEADLESS = "headless";
    public static final String RESOLUTION = "resolution";
    public static final String BASE_URL = "base.url";
    public static final String API_BASE_URL = "api.base.url";

    // Web driver timeouts (requirement.md section 24)
    public static final String PAGE_LOAD_TIMEOUT = "page.load.timeout";
    public static final String SCRIPT_TIMEOUT = "script.timeout";
    public static final String IMPLICIT_WAIT_TIMEOUT = "implicit.wait.timeout";
    public static final String EXPLICIT_WAIT_TIMEOUT = "explicit.wait.timeout";
    public static final String POLLING_INTERVAL = "polling.interval";

    // Screenshots (requirement.md section 19)
    public static final String SCREENSHOT_MODE = "screenshot.mode";

    // API timeouts (requirement.md section 24)
    public static final String API_CONNECTION_TIMEOUT = "api.connection.timeout";
    public static final String API_SOCKET_TIMEOUT = "api.socket.timeout";

    // Retry (requirement.md section 23)
    public static final String RETRY_MAX_COUNT = "retry.max.count";

    // Mobile / Appium (requirement.md section 8) - validated lazily at driver-creation
    // time, not at ConfigManager startup, since Web-only/API-only runs need none of these.
    public static final String MOBILE_PLATFORM = "mobile.platform";
    public static final String MOBILE_DEVICE_NAME = "mobile.device.name";
    public static final String MOBILE_PLATFORM_VERSION = "mobile.platform.version";
    public static final String MOBILE_AUTOMATION_NAME = "mobile.automation.name";
    public static final String MOBILE_APP_PATH = "mobile.app.path";
    public static final String MOBILE_UDID = "mobile.udid";
    public static final String MOBILE_APP_PACKAGE = "mobile.app.package";
    public static final String MOBILE_APP_ACTIVITY = "mobile.app.activity";
    public static final String MOBILE_APP_WAIT_ACTIVITY = "mobile.app.wait.activity";
    public static final String MOBILE_BUNDLE_ID = "mobile.bundle.id";
    public static final String APPIUM_SERVER_URL = "appium.server.url";
    public static final String APPIUM_COMMAND_TIMEOUT = "appium.command.timeout";

    // Mobile device provider (requirement.md section 34 - cloud device farm extensibility).
    // LOCAL (default) covers an emulator/simulator or a physical device on this machine
    // alike; BROWSERSTACK routes the same test through BrowserStack's device cloud instead -
    // see DriverFactory and README.md's "Mobile device providers" section. BrowserStack
    // credentials (BROWSERSTACK_USERNAME/BROWSERSTACK_ACCESS_KEY) are secrets, resolved via
    // SecretManager, never a plain config value (RULE 6).
    public static final String MOBILE_DEVICE_PROVIDER = "mobile.device.provider";
    public static final String BROWSERSTACK_SERVER_URL = "browserstack.server.url";
    public static final String BROWSERSTACK_APP_ID = "browserstack.app.id";
    public static final String BROWSERSTACK_PROJECT_NAME = "browserstack.project.name";
    public static final String BROWSERSTACK_BUILD_NAME = "browserstack.build.name";
}
