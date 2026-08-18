# Web-Mobile-API Automation Framework

A single, enterprise-grade test automation framework covering **Web** (Selenium), **Mobile**
(Appium), and **API** (REST Assured) — one framework core, one configuration model, one
reporting pipeline, driven by TestNG. Built in 14 phases against the master prompt in
[`requirement.md`](requirement.md), validated at every phase against real, live systems
(a real web app, a real mobile app, a real API, a real account) rather than mocks.

## Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project structure](#project-structure)
4. [Installation](#installation)
5. [Maven commands](#maven-commands)
6. [Environment configuration](#environment-configuration)
7. [Secret configuration](#secret-configuration)
8. [Test data management](#test-data-management)
9. [Web automation example](#web-automation-example)
10. [Mobile automation example](#mobile-automation-example)
11. [API automation example](#api-automation-example)
12. [API chaining example](#api-chaining-example)
13. [Parallel execution](#parallel-execution)
14. [Reporting](#reporting)
15. [CI/CD](#cicd)
16. [Adding a new test](#adding-a-new-test)
17. [Adding a new page](#adding-a-new-page)
18. [Adding a new API service](#adding-a-new-api-service)
19. [Troubleshooting](#troubleshooting)

---

## Overview

Three automation surfaces, one framework:

| Surface | Library | Package |
|---|---|---|
| Web | Selenium WebDriver | `com.framework.web` |
| Mobile | Appium | `com.framework.mobile` |
| API | REST Assured | `com.framework.api` |

All three sit on shared infrastructure: configuration (`com.framework.config`), secrets
(`com.framework.secrets`), thread-safe driver/context management (`com.framework.driver`,
`com.framework.context`), test data (`com.framework.testdata`), logging (SLF4J/Logback,
Phase 10), and reporting (`com.framework.reporting`, Extent + Allure, Phase 11). Test code
(`com.tests.*`) depends on the framework; the framework never depends on test code.

## Architecture

```
                    TESTNG TESTS
                          |
          +---------------+---------------+
          |               |               |
         WEB            MOBILE            API
      Selenium          Appium        REST Assured
          |               |               |
     Page Objects    Page Objects     API Services
          |               |               |
          +---------------+---------------+
                          |
                   FRAMEWORK CORE
                          |
     +--------------------+---------------------+
     |          |          |         |          |
 Configuration Driver    Data     Logging   Reporting
 Management    Management Management           |
     |          |          |         |          |
     +----------+----------+---------+----------+
                          |
                  Parallel Execution
```

**Thread-safety is the load-bearing design constraint**, not an afterthought — every
static/singleton object in the framework is classified as one of five categories
(immutable-global, thread-safe-singleton, thread-local, test-scoped, suite-scoped) and lives
up to that classification. The full audit, including two real bugs it caught, is in
[`THREAD_SAFETY_AUDIT.md`](THREAD_SAFETY_AUDIT.md).

**One variable-resolution mechanism spans the whole framework**: `${{KEY}}` resolves against
secrets, configuration, and runtime/API-context values through the same
`PlaceholderResolver`, in test data, request bodies, and anywhere else text is resolved — see
[Secret configuration](#secret-configuration) and [API chaining example](#api-chaining-example).

## Project structure

```
src/main/java/com/framework/
    api/            ApiClient, ApiRequest, ApiResponse, ApiContext, ApiHeaders, ApiUtils
        requests/   Request DTOs (Java records)
        responses/  Response DTOs (Java records)
        services/   AuthenticationService, EventService, BookingService
    config/         ConfigManager (4-tier precedence)
    constants/      ConfigKeys
    context/        VariableManager (thread-safe runtime variable store)
    driver/         DriverFactory, DriverManager, WebDriverManager, MobileDriverManager
    enums/          BrowserType, Environment, MobilePlatformType, ScreenshotMode
    exceptions/     FrameworkException and its subtypes
    listeners/      TestNG listeners - see the list below
    mobile/         BaseMobilePage, BaseMobileComponent, MobileActions, MobileUtils, MobileWaits
    reporting/      ExtentManager, ExtentLoggingAppender, AllureManager, ReportManager
    secrets/        SecretManager, SensitiveDataMasker
    testdata/       TestDataManager, TestData, JSON/YAML/CSV/Excel readers, PlaceholderResolver
    utils/          JsonUtils, FileUtils, ScreenshotUtils, DateUtils, RandomDataUtils, EnumUtils
    web/            BasePage, BaseComponent, WebActions, WebUtils, WebWaits

src/test/java/com/tests/
    api/            API tests (authentication, chaining, data-driven)
    base/           Framework-level validation (config, secrets, masking, retry, test data)
    mobile/         Mobile tests + Page Objects (Sauce Labs SwagLabs demo app)
    web/            Web tests + Page Objects + Components (eventhub.rahulshettyacademy.com)

src/main/resources/
    logback.xml     Console + rolling-file logging, MDC test-tagging
    META-INF/services/org.testng.ITestNGListener   ServiceLoader-registered listeners

src/test/resources/
    testdata/       json/, yaml/, csv/, excel/ sample data files
    (no suite XML - every run is driven by command-line flags; see Maven commands)

config/             dev/qa/uat/staging.properties, each fully self-contained (no shared
                    default.properties layer) - at the repo root, not under
                    src/main/resources, so a tester can find and edit an environment file
                    directly (wired into the classpath via pom.xml's <resources>, not just
                    a copy - see Environment configuration)
apps/               Mobile binaries (swaglabs.apk, swag.app)
THREAD_SAFETY_AUDIT.md, CI_CD.md, README.md   Documentation
.github/workflows/, Jenkinsfile, .gitlab-ci.yml   CI/CD pipelines
```

**Listeners** (`com.framework.listeners`, all auto-registered via `META-INF/services`):

| Listener | Job |
|---|---|
| `TestLoggingContextListener` | Tags every log line with `[ClassName.methodName]` via MDC |
| `RetryAnalyzerTransformer` | Assigns `RetryAnalyzer` to every `@Test` automatically |
| `ConfigParameterListener` | Bridges TestNG `<parameter>`s into `ConfigManager` |
| `ApiContextListener` | Clears API/runtime context around every test |
| `DriverCleanupListener` | Quits WebDriver/AppiumDriver after every test |
| `ExtentReportingListener` | Creates/finalizes the Extent report node per test |
| `ScreenshotCaptureListener` | Captures + attaches a failure screenshot to both reports |

Package-info Javadoc in each package documents intent; class-level Javadoc documents design
decisions and, where relevant, the live bug that decision was found by fixing.

**Note on a deliberate omission**: the target architecture (see `requirement.md` §4)
suggests a `com.framework.logging` package with a `LoggerManager`. It was never built —
SLF4J itself already serves as the logging facade the framework needs, `logback.xml` (Phase
10) covers configuration, and `ExtentLoggingAppender`/`TestLoggingContextListener` (Phases
10-11, in `reporting`/`listeners`) cover the integration work a `LoggerManager` would
otherwise exist for. A wrapper class with no real job of its own would be exactly what
requirement.md §28 warns against.

## Installation

Prerequisites:
- JDK 17+
- Maven 3.9+
- Chrome and/or Firefox (Web tests) - Selenium Manager, bundled with Selenium 4, resolves
  the matching driver binary automatically; nothing to install separately.
- Appium 3.x + an Android emulator/iOS simulator (Mobile tests only - see
  [Troubleshooting](#troubleshooting))

```bash
git clone <repo-url>
cd web-mobile-api-framework
cp .secret.env.example .secret.env   # fill in real values - see Secret configuration
mvn clean compile
```

## Maven commands

**No suite XML anywhere in this repo.** Every test-selection concern - an individual test or
method, a group, several groups, exclusions, parallel mode/thread count, browser,
environment - is a plain command-line `-D` flag against Surefire's own classpath-wide TestNG
discovery. Picking a different subset is never a file edit:

```bash
# Compile
mvn clean compile

# Default run: every group except mobile (no local emulator by default) and
# frameworkSelfTest (a deliberately-always-failing test - see Troubleshooting)
mvn test -DexcludedGroups=mobile,frameworkSelfTest

# A specific environment/browser/group combination (the shape requirement.md's own
# example uses)
mvn clean test -Denv=qa -Dgroups=smoke -Dbrowser=chrome -Dheadless=true

# A single test class
mvn test -Dtest=AuthenticationTest

# A single test method
mvn test -Dtest=AuthenticationTest#loginWithExistingAccountWorks

# Several test classes at once
mvn test -Dtest=AuthenticationTest,EventBookingChainingTest

# One or more groups (comma-separated) - smoke/sanity/regression/whatever groups your
# @Test(groups = ...) tags actually use; nothing here is hardcoded
mvn test -Dgroups=smoke
mvn test -Dgroups=smoke,api

# Sanity: the narrowest possible checkpoint - one representative live test per surface
# (Web/Mobile/API), smaller than smoke (which also covers every framework-internal unit test)
mvn test -Dgroups=sanity -DexcludedGroups=mobile

# Resume mobile testing (emulator/Appium must be running - see Troubleshooting)
mvn test -Dgroups=mobile

# Real parallel execution - -Dparallel/-DthreadCount are plain Surefire/TestNG system
# properties, confirmed live to genuinely parallelize whatever -Dgroups/-Dtest already
# selected, no suite XML needed
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4

# Validate RetryAnalyzer's behavior deliberately (this always shows one designed failure)
mvn test -Dgroups=frameworkSelfTest
```

`-DexcludedGroups=mobile,frameworkSelfTest` is the closest thing to a canonical "everything
green" command for this repo; see [Troubleshooting](#troubleshooting) for why both
exclusions exist and are *not* accidental gaps.

## Environment configuration

Environment files live at **`config/` in the repo root** (`config/dev.properties`,
`config/qa.properties`, `config/uat.properties`, `config/staging.properties`) - not nested
under `src/main/resources`, so a tester can find and edit one directly. `pom.xml`'s
`<resources>` puts it on the runtime classpath at build time; `ConfigManager` doesn't know or
care that the physical source moved.

**No shared `default.properties`** - each environment file is fully self-contained (one file
to read, no hidden merge). **`qa` is the default environment** when `-Denv` isn't given.

4-tier precedence (highest wins), all resolved through `ConfigManager`:

```
Test-specific override  (ConfigManager.setOverride, thread-local)
        |
TestNG <parameter>       (suite/test XML)
        |
System property          (-Dkey=value)
        |
config/{env}.properties  (qa by default; dev, uat, staging by -Denv=...)
```

```bash
mvn test                                    # qa.properties, no flag needed
mvn test -Denv=dev -Dbrowser=chrome -Dheadless=true   # any other environment
```

An unsupported `env`, a missing `config/{env}.properties`, or a missing/blank required key
(`browser`, `base.url`, `api.base.url`) throws `ConfigurationException` immediately at
startup - never mid-test (requirement.md §31, fail-fast).

## Secret configuration

```bash
cp .secret.env.example .secret.env
```

```
EVENTHUB_EMAIL=your-eventhub-account@example.com
EVENTHUB_PASSWORD=your-eventhub-password
```

`.secret.env` is git-ignored and must never be committed. Precedence (highest wins):

```
CI/CD environment variable  (System.getenv, set as a GitHub/Jenkins/GitLab secret)
        |
.secret.env  (local development only)
```

Every value `SecretManager.get(...)` resolves is registered with `SensitiveDataMasker`
automatically and masked everywhere the framework logs or reports text from then on -
callers never remember to mask it themselves. One caveat isn't automatic, though: see
[Troubleshooting](#troubleshooting)'s "masking is opt-in per call site" note.

`${{KEY}}` placeholders in test data resolve against secrets, configuration, and runtime
context through one shared `PlaceholderResolver`:

```json
{ "validLogin": { "email": "${{EVENTHUB_EMAIL}}", "password": "${{EVENTHUB_PASSWORD}}" } }
```

## Test data management

One entry point regardless of source format:

```java
TestDataManager.load("login.json").get("validLogin", AuthRequest.class);
TestDataManager.load("events.csv").dataProvider(CreateEventRequest.class);
```

| Format | Folder | Shape |
|---|---|---|
| JSON | `testdata/json/` | Object-root (named records) or array-root |
| YAML | `testdata/yaml/` | Same two shapes as JSON |
| CSV | `testdata/csv/` | Row-oriented; a `name` column enables name lookup |
| Excel | `testdata/excel/` | Same as CSV, first sheet, header row + data rows |

Each file's raw records are cached once (thread-safe, immutable); `${{...}}` placeholders
resolve fresh on every access, not at load time - so a value produced by an earlier API call
mid-test (e.g. `${{eventId}}`) still resolves correctly. See `TestData`'s Javadoc for the
full reasoning, and `TestDataManagerTest`/`DataDrivenLoginTest`/`DataDrivenEventCreationTest`
for real, live-validated usage of all four formats plus TestNG `@DataProvider` integration.

## Web automation example

```java
public class LoginTest {
    @Test(groups = {"smoke", "web"})
    public void validLoginNavigatesToHomePage() {
        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
                .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
                .clickLogin();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed());
    }
}
```

Page Objects extend `BasePage`, expose business-level actions (never raw
`driver.findElement(...)`), and log each step (`logger.info("Entering email")`) - which
Phase 11's `ExtentLoggingAppender` then mirrors into the Extent report automatically, with no
extra reporting code. See `src/test/java/com/tests/web/`.

## Mobile automation example

```java
public class LoginTest {
    @BeforeMethod(alwaysRun = true)
    public void launchApp() {
        MobileDriverManager.getDriver(); // triggers app launch for this thread
    }

    @Test(groups = {"smoke", "mobile"})
    public void standardUserCanLogIn() {
        new LoginPage()
                .enterUsername(USERNAME)
                .enterPassword(PASSWORD)
                .tapLogin();

        assertTrue(new ProductsPage().isDisplayed());
    }
}
```

Mirrors the Web layer's structure exactly (`BaseMobilePage`, `MobileActions`, W3C
`PointerInput` gestures, not the deprecated `TouchAction` API). The same `test-*`
accessibility identifiers work unmodified on both the Android emulator and iOS simulator -
verified by running the same Page Object classes (`com.tests.mobile.pages.*`) against both,
with no code change, only config. See `src/test/java/com/tests/mobile/`, and
[Troubleshooting](#troubleshooting) for resuming local mobile infra.

Available in **every** environment (`-Denv=dev/qa/uat/staging`), not just one - each
`config/{env}.properties` carries the same local Android default plus a commented-out iOS
alternative (see the file for exact values). Switch platform per-run without editing
anything:

```bash
# Android (the active default in every config/{env}.properties)
mvn test -Dgroups=mobile

# iOS - override the four keys that differ (adjust device name/version to whatever
# simulator you actually have booted: `xcrun simctl list devices available`)
mvn test -Dgroups=mobile -Dmobile.platform=ios -Dmobile.device.name="iPhone 17 Pro" \
    -Dmobile.platform.version=26.2 -Dmobile.app.path=apps/swag.app
```

### Mobile device providers (requirement.md §34 - cloud device farm extensibility)

The same test/Page Object code runs unchanged against an emulator, a physical device, or a
cloud device farm - only `mobile.device.provider` and a few related config values change,
regardless of which `config/{env}.properties` is active:

| Provider | `mobile.device.provider` | Notes |
|---|---|---|
| Emulator/simulator | `LOCAL` (default) | Every `config/{env}.properties`'s Android/iOS blocks above. |
| Physical device | `LOCAL` | Same local Appium server; set `mobile.udid` to the device's serial (`adb devices`)/UDID instead of/alongside `mobile.device.name`. |
| BrowserStack | `BROWSERSTACK` | Routes through BrowserStack's device cloud instead of a local Appium server. |

```bash
mvn test -Dgroups=mobile -Dmobile.device.provider=BROWSERSTACK \
    -Dmobile.device.name="Samsung Galaxy S23" -Dmobile.platform.version=13 \
    -Dbrowserstack.app.id=bs://<app-id-from-browserstack-upload>
```

`BROWSERSTACK_USERNAME`/`BROWSERSTACK_ACCESS_KEY` are secrets (`.secret.env.example`), never
a plain config value. The app itself must already be uploaded to BrowserStack (their
[app-upload API](https://www.browserstack.com/docs/app-automate/appium/upload-app) returns
the `bs://...` ID `browserstack.app.id` expects) - this framework doesn't automate that
upload step itself, deliberately: it's a one-time-per-app-version action, not a per-test-run
one, and doesn't belong in the driver-creation hot path.

### Parallel mobile execution

Concurrent local Android/iOS sessions (multiple emulators, multiple physical devices, or a
mix) need distinct `systemPort`/`wdaLocalPort` values per session or they collide -
`MobilePortAllocator` allocates a fresh one on every mobile driver creation automatically, no
config needed (not relevant for BrowserStack, which isolates devices server-side - see
`THREAD_SAFETY_AUDIT.md`).

A **device matrix** (`testdata/json/mobile-devices.json`) plus TestNG's own
`@DataProvider(parallel = true)` drives a real cross-platform parallel run -
`MultiDeviceParallelTest` launches every row in the matrix concurrently, each on its own
thread, each overriding `mobile.platform`/`device.name`/`platform.version`/`app.path` via the
same thread-local `ConfigManager.setOverride` mechanism
`LoginTest.loginWorksAcrossMultipleBrowsers` already uses for browsers - just dispatched
concurrently here instead of sequentially. Live-verified against a real Android emulator
*and* a real iOS simulator launching the same app **at the same time** on different threads,
not simulated. Add a row to the JSON file for any additional device (another emulator,
another physical device) - no code change needed.

```bash
mvn test -Dtest=MultiDeviceParallelTest
```

## API automation example

```java
public class AuthenticationTest {
    private final AuthenticationService authService = new AuthenticationService();

    @Test(groups = {"smoke", "api"})
    public void loginWithExistingAccountWorks() {
        AuthResponse response = authService.login(
                SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));

        assertTrue(response.success());
    }
}
```

`AuthenticationService`/`EventService`/`BookingService` wrap `ApiClient` (the one place REST
Assured is actually called from); every request/response is logged (masked) and, since
Phase 11, attached to the Allure report automatically. See `src/test/java/com/tests/api/`.

## API chaining example

```java
ApiResponse createEventResponse = eventService.createEvent(eventRequest);
int eventId = createEventResponse.jsonPath().getInt("data.id");
ApiContext.set("eventId", String.valueOf(eventId));

CreateBookingRequest bookingRequest = new CreateBookingRequest(
        Integer.parseInt(ApiContext.get("eventId")), "Context Tester", "context.tester@example.com",
        "+91-9876500001", 1);
ApiResponse createBookingResponse = bookingService.createBooking(bookingRequest);
```

`ApiContext` (thread-local, backed by `VariableManager`) is the thread-safe context/variable
manager requirement.md §11 requires. It also self-registers as a `PlaceholderResolver`
source, so a chained value resolves as `${{eventId}}` in test data too, and it absorbed
`ApiClient`'s bearer-token storage (Phase 8), so `${{accessToken}}` works the same way. See
`ApiContextChainingTest` for the full round trip, including a real
`invocationCount=20, threadPoolSize=8` proof that chained values never bleed across threads.

## Parallel execution

```bash
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4
```

`-Dparallel`/`-DthreadCount` are plain Surefire/TestNG system properties - no suite XML
needed, confirmed live with genuinely concurrent Chrome/Firefox sessions and API calls
interleaved on different threads (distinct thread names/overlapping timestamps in
`logs/framework.log`). `-Dparallel=classes` runs each selected class on its own thread;
`-Dparallel=methods` parallelizes at the method level, already proven directly by the
`invocationCount`/`threadPoolSize` stress tests in `ApiContextChainingTest`/`TestDataManagerTest`.

Mobile parallelizes the same way (`-Dgroups=mobile -Dparallel=...`), with one addition:
concurrent local Android/iOS sessions need distinct `systemPort`/`wdaLocalPort` values or
they collide port-for-port - `MobilePortAllocator` hands out a fresh one on every mobile
driver creation automatically (see "Mobile device providers" under
[Mobile automation example](#mobile-automation-example)), no config needed. Requires
multiple emulators/devices/BrowserStack capacity to actually exercise, obviously - a single
local emulator can only run one session at a time regardless of thread count.

Full classification of every thread-shared object, plus real ordering/port-collision bugs
this uncovered and fixed, is in [`THREAD_SAFETY_AUDIT.md`](THREAD_SAFETY_AUDIT.md).

## Reporting

Both Extent and Allure, automatically, with no reporting code in tests/Page Objects:

- **Extent** (`reports/extent/index.html`, self-contained HTML): `ExtentLoggingAppender`
  mirrors every `com.framework`/`com.tests` log line into the current test's report node
  automatically - it's a Logback appender, wired in `logback.xml`, not a manual call.
- **Allure** (`allure-results/`, raw JSON - `allure serve allure-results` to view):
  `allure-testng` captures results/groups/retries/`@Before`/`@AfterMethod` natively;
  `AllureManager` adds masked API request/response and screenshot attachments on top.
- **Screenshots**: `screenshot.mode` = `FAILURE` | `EVERY_ACTION` | `DISABLED`. On failure,
  `ScreenshotCaptureListener` captures and attaches to both reports via `ReportManager`.
- **Retry**: `RetryAnalyzer` (max attempts via `retry.max.count`, default 1) retries
  everything except `AssertionError` - a retried test's original failed attempt keeps its
  own report entry, labeled `(Retry N)`, never silently overwritten.

## CI/CD

Ready-to-use pipelines for all three platforms requirement.md names - see
[`CI_CD.md`](CI_CD.md) for the full picture (secrets setup, artifact locations, browser
availability per platform, and two real findings from actually running these commands
rather than trusting them):

| Platform | File |
|---|---|
| GitHub Actions | `.github/workflows/ci.yml` |
| Jenkins | `Jenkinsfile` |
| GitLab CI | `.gitlab-ci.yml` |

## Adding a new test

```java
public class MyNewTest {
    @Test(groups = {"smoke", "api"})   // or "web" / "mobile"
    public void myNewScenario() {
        // business-level actions only - no raw Selenium/Appium/REST Assured calls here
    }
}
```

Retry (`RetryAnalyzer`), MDC log-tagging, Extent/Allure reporting, and driver cleanup all
apply automatically via the auto-registered listeners - nothing to wire up per test. If the
method needs setup, add `@BeforeMethod(alwaysRun = true)` - see
[Troubleshooting](#troubleshooting) for why `alwaysRun` is not optional in this codebase.

## Adding a new page

```java
public class MyNewPage extends BasePage {
    private static final By SOME_ELEMENT = By.id("some-id");

    public MyNewPage doSomething() {
        logger.info("Doing something");   // becomes an Extent report step automatically
        click(SOME_ELEMENT);
        return this;
    }
}
```

Extend `BasePage` (Web) or `BaseMobilePage` (Mobile). Expose business-level actions that
return `this` for chaining, or the next Page Object when a navigation genuinely completes.
For a repeated element (a card, a row), extend `BaseComponent`/`BaseMobileComponent` instead
and take an already-located root element in the constructor - see `EventCardComponent`.

## Adding a new API service

```java
public final class MyNewService {
    public ApiResponse doSomething(MyRequest request) {
        return ApiClient.execute(ApiRequest.post("/my-endpoint").body(request));
    }
}
```

One method per endpoint, returning `ApiResponse` (or a parsed DTO if the caller always needs
it parsed - see `AuthenticationService`). Never call REST Assured directly outside
`ApiClient`. Request/response DTOs are Java records under `api/requests`/`api/responses`.

## Troubleshooting

**`mvn test` with no flags fails on `RetryBehaviorTest`** - expected.
`assertionFailureIsNeverRetried` fails on *every* invocation by design, to prove
`RetryAnalyzer` never retries a real assertion failure. It's tagged `frameworkSelfTest`,
excluded from the standard run: `mvn test -DexcludedGroups=mobile,frameworkSelfTest`.

**Mobile tests fail with `SessionNotCreated`** - no emulator/Appium server running. Start
both (`emulator -avd <name> -no-snapshot -no-boot-anim &` and `appium --base-path /wd/hub &`),
or run without mobile: `-DexcludedGroups=mobile,frameworkSelfTest`.

**`-Dgroups=X` runs zero tests but still reports `BUILD SUCCESS`** - `X` isn't a group any
`@Test` is actually tagged with (the real ones: `smoke`, `sanity`, `api`, `web`, `mobile`,
`frameworkSelfTest`). Surefire/TestNG don't fail on an empty group match by default; check
the actual test count in the summary, not just the exit code.

**A `@BeforeMethod`-driven login/setup silently doesn't run under `-Dgroups=X`, and the
`@Test` fails with something like 401 Unauthorized** - every `@BeforeMethod` in this codebase
declares `alwaysRun = true` for exactly this reason: TestNG silently skips a `@BeforeMethod`
with no `groups` of its own whenever a group *include* filter is active, even though the
`@Test` it sets up for still runs. If you add a new `@BeforeMethod`, add `alwaysRun = true`
too - see `CI_CD.md`'s "found in practice" note for the full story.

**Masking is opt-in per call site, not automatic anywhere.** `SensitiveDataMasker.mask(...)`
must be called explicitly wherever text might contain a secret - it isn't wired into
Logback/SLF4J globally. A real leak (an unmasked email in a log line one class away from a
correctly-masked one) was caught this way in Phase 10; if you add a new log/report call site
that might carry a secret, mask it there.

**A `@DataProvider` row type with a secret field can leak it into `allure-results/` even if
every framework log/report call site is masked correctly.** `allure-testng` automatically
records every `@DataProvider` row into the Allure result's `parameters` via the row object's
own `toString()` - entirely outside framework code, via its own AspectJ interceptor, so
`SensitiveDataMasker` never gets a chance to run. Found live in Phase 14:
`DataDrivenLoginTest.LoginAttempt`'s auto-generated record `toString()` put a raw password
straight into `allure-results/*-result.json`. Fixed with a custom `toString()` masking the
field (see `LoginAttempt`'s Javadoc). Any new `@DataProvider` row type carrying a secret
needs the same treatment - there is no central fix for this one.

**A Web test's browser configuration (headless, browser choice) looks wrong under
`parallel="classes"`** - `ConfigParameterListener` resets/repopulates config before *every*
invoked method (not just `onTestStart`, which fires after `@BeforeMethod` and is too late for
config a `@BeforeMethod`-triggered driver creation needs) - this was a real Phase 12 bug,
already fixed; if it recurs, check that whatever's reading config isn't caching a value from
before the fix's reasoning applies.

**`Log4j2 could not find a logging implementation` on the console** - harmless. Apache POI's
own internal logging (used by the Excel test-data reader) falls back to a SimpleLogger; it
does not affect this framework's own SLF4J/Logback output or test results.

**CDP version warnings from Selenium** (`Unable to find CDP implementation matching ...`) -
harmless; Chrome's DevTools Protocol version is newer than Selenium's bundled CDP client.
Does not affect test execution.
