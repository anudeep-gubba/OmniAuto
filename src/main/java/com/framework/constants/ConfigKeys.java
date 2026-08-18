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

    // Test data (requirement.md section 15) - the default source format TestDataManager.load()
    // resolves an extension-less file name against, e.g. "login" -> testdata/json/login.json
    // when this is "json". An explicit extension in the call (e.g. "login.yaml") always wins
    // over this. One of: json | yaml | csv | excel.
    public static final String TEST_DATA_FORMAT = "testdata.format";

    // Mobile / Appium (requirement.md section 8) - validated lazily at driver-creation
    // time, not at ConfigManager startup, since Web-only/API-only runs need none of these.
    // Device details (name/platform.version/app.path) are not set here - a mobile run
    // resolves them from config/mobile-devices.json instead (see
    // com.framework.driver.MobileDeviceMatrix/MobileDevicePool); MOBILE_PLATFORM here only
    // picks androidList vs iosList for a sequential (non -Dparallel) run. An explicit
    // MOBILE_DEVICE_NAME (-D or a test override, e.g. MultiDeviceParallelTest setting one per
    // matrix row) always wins and skips that resolution entirely.
    public static final String MOBILE_PLATFORM = "mobile.platform";
    public static final String MOBILE_DEVICE_NAME = "mobile.device.name";
    public static final String MOBILE_PLATFORM_VERSION = "mobile.platform.version";
    public static final String MOBILE_AUTOMATION_NAME = "mobile.automation.name";
    public static final String MOBILE_APP_PATH = "mobile.app.path";

    // Which app binary a resolved device's platform installs (requirement.md section 8) - an
    // environment/build concern, so it lives in config/{env}.properties, not the device
    // inventory in config/mobile-devices.json. Every device on a platform runs the same app
    // build, so this is per-platform, not per-device (see MobileDeviceMatrix#loadDevice).
    public static final String MOBILE_APP_PATH_ANDROID = "mobile.app.path.android";
    public static final String MOBILE_APP_PATH_IOS = "mobile.app.path.ios";
    public static final String MOBILE_UDID = "mobile.udid";
    public static final String MOBILE_APP_PACKAGE = "mobile.app.package";
    public static final String MOBILE_APP_ACTIVITY = "mobile.app.activity";
    public static final String MOBILE_APP_WAIT_ACTIVITY = "mobile.app.wait.activity";
    public static final String MOBILE_BUNDLE_ID = "mobile.bundle.id";
    public static final String APPIUM_SERVER_URL = "appium.server.url";
    public static final String APPIUM_COMMAND_TIMEOUT = "appium.command.timeout";

    // Android WebView/Chrome-hybrid content (requirement.md section 20/34) - true only for a
    // device whose config/mobile-devices.json entry sets "hybrid": true; requests a
    // chromedriverPort be allocated alongside systemPort (see MobilePortAllocator). Not
    // applicable to iOS.
    public static final String MOBILE_HYBRID = "mobile.hybrid";

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
