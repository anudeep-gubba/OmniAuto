# OmniAuto — Web-Mobile-API Automation Framework

One framework covering **Web** (Selenium), **Mobile** (Appium), and **API** (REST Assured) —
shared configuration, secrets, driver/context management, test data, logging, and reporting,
driven entirely by TestNG command-line flags. No suite XML anywhere.

**Stack:** JDK 17 · Maven · TestNG 7.10 · Selenium 4.25 · Appium 9.3 · REST Assured 5.5 ·
Extent 5.1 + Allure 2.29 · Logback

## Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project structure](#project-structure)
4. [Setup](#setup)
5. [Configuration](#configuration)
6. [Test data](#test-data)
7. [Running tests](#running-tests)
8. [Examples](#examples)
9. [Parallel execution](#parallel-execution)
10. [Reporting](#reporting)
11. [CI/CD](#cicd)
12. [BrowserStack](#browserstack)
13. [Writing tests](#writing-tests)
14. [Thread safety](#thread-safety)
15. [Troubleshooting](#troubleshooting)

---

## Overview

| Surface | Library | Package |
|---|---|---|
| Web | Selenium WebDriver | `com.framework.web` |
| Mobile | Appium | `com.framework.mobile` |
| API | REST Assured | `com.framework.api` |

Test code (`com.tests.*`) depends on the framework (`com.framework.*`); the framework never
depends on test code.

**Key ideas:**

- **Thread-safety is load-bearing.** Every shared object is either immutable, a
  concurrent-safe structure, or `ThreadLocal` — see [Thread safety](#thread-safety).
- **One placeholder syntax everywhere.** `${{KEY}}` resolves against secrets, config, and
  runtime/API context through a single `PlaceholderResolver`, in test data, request bodies,
  anywhere text is resolved.
- **No suite XML.** Class, method, group, browser, environment, parallel mode — all plain
  `-D` flags. Picking a different subset is never a file edit.
- **Zero boilerplate per test.** Retry, log-tagging, Extent/Allure reporting, and driver
  cleanup are automatic via TestNG listeners.

**Known limitations:**

- **Mobile needs local infra** (emulator/simulator + Appium server) — not available on a
  hosted CI runner, so `mobile` is excluded from CI by default. Use a self-hosted runner or
  BrowserStack.
- **`@DataProvider` rows can leak secrets into Allure.** `allure-testng` records every row via
  its own `toString()`, bypassing the masker — give any row type with a secret field a custom
  masking `toString()` (see `DataDrivenLoginTest.LoginAttempt`).
- **A missing `alwaysRun = true` fails silently** — TestNG skips a `@BeforeMethod` with no
  groups of its own whenever `-Dgroups=` is active, so its `@Test` runs unset-up (e.g. 401s).
- **BrowserStack app upload is manual** — one-time per app version, via their own API.
- **An empty `-Dgroups=X` match still reports `BUILD SUCCESS`** — always check the printed
  test count.

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
      Configuration · Driver · Data · Logging · Reporting
                          |
                  Parallel Execution
```

## Project structure

```
src/main/java/com/framework/
    api/            ApiClient, ApiRequest/Response, ApiContext, ApiHeaders
        requests/   Request DTOs (Java records)
        responses/  Response DTOs (Java records)
        services/   AuthenticationService, EventService, BookingService
    config/         ConfigManager (4-tier precedence)
    constants/      ConfigKeys
    context/        VariableManager (thread-safe runtime variable store)
    driver/         DriverFactory, WebDriverManager, MobileDriverManager, MobilePortAllocator
    enums/          BrowserType, Environment, MobilePlatformType, MobileDeviceProvider, ScreenshotMode
    exceptions/     FrameworkException and subtypes
    listeners/      TestNG listeners (see table below)
    mobile/         BaseMobilePage, BaseMobileComponent, MobileActions, MobileUtils, MobileWaits
    reporting/      ExtentManager, ExtentLoggingAppender, AllureManager, ReportManager
    secrets/        SecretManager, SensitiveDataMasker
    testdata/       TestDataManager, TestData, JSON/YAML/CSV/Excel readers, PlaceholderResolver
    utils/          JsonUtils, FileUtils, ScreenshotUtils, DateUtils, RandomDataUtils, EnumUtils
    web/            BasePage, BaseComponent, WebActions, WebUtils, WebWaits

src/test/java/com/tests/
    api/     API tests (authentication, chaining, data-driven)
    base/    Framework-level validation (config, secrets, masking, retry, test data)
    mobile/  Mobile tests + Page Objects (Sauce Labs SwagLabs demo app)
    web/     Web tests + Page Objects + Components (eventhub.rahulshettyacademy.com)

config/             dev/qa.properties + mobile-devices.json — repo root, not
                    src/main/resources, so they're easy to find and edit; each
                    {env}.properties file is fully self-contained
apps/               Mobile binaries (swaglabs.apk, swag.app)
.github/workflows/  CI pipeline (GitHub Actions)
```

**Listeners** (auto-registered via `META-INF/services`):

| Listener | Job |
|---|---|
| `TestLoggingContextListener` | Tags every log line with `[ClassName.methodName]` via MDC |
| `RetryAnalyzerTransformer` | Assigns `RetryAnalyzer` to every `@Test` |
| `ConfigParameterListener` | Bridges TestNG `<parameter>`s into `ConfigManager`, reset before every method |
| `ApiContextListener` | Clears API/runtime context around every test |
| `DriverCleanupListener` | Quits WebDriver/AppiumDriver after every test |
| `ExtentReportingListener` | Creates/finalizes the Extent report node per test |
| `ScreenshotCaptureListener` | Captures + attaches a failure screenshot to both reports |

## Setup

**Required everywhere:** JDK 17+, Maven 3.9+, Git.

**Web:** Chrome and/or Firefox — Selenium Manager resolves drivers automatically. Safari
needs `safaridriver --enable` plus Safari > Develop > Allow Remote Automation (no headless
mode).

**Mobile:** Appium 3.x (`npm i -g appium`) with `uiautomator2` (Android) and/or `xcuitest`
(iOS) drivers installed. Android needs a booted AVD emulator; iOS (macOS only) needs a booted
Simulator.

```bash
git clone <repo-url> && cd OmniAuto
cp .secret.env.example .secret.env   # fill in real values
mvn clean compile

# Mobile only, before -Dgroups=mobile: boot an emulator/simulator, then
appium --base-path /wd/hub
```

## Configuration

**Environment files** live in `config/` at the repo root (`dev`/`qa` `.properties` — the only
two environments this repo currently exercises; add another by adding a constant to
`Environment` plus its own `config/{env}.properties`), each fully self-contained — no shared
`default.properties`. `qa` is the default when `-Denv` is omitted.

4-tier precedence (highest wins):

```
Test-specific override (ConfigManager.setOverride)  >  TestNG <parameter>  >
System property (-Dkey=value)  >  config/{env}.properties
```

```bash
mvn test                                              # qa.properties
mvn test -Denv=dev -Dbrowser=chrome -Dheadless=true   # any other environment
```

A missing `config/{env}.properties` or a missing/blank required key (`browser`, `base.url`,
`api.base.url`) throws immediately at startup, never mid-test.

**Secrets** — `.secret.env` (git-ignored, never commit it), overridden by real CI/CD
environment variables:

| Secret key | Used for |
|---|---|
| `EVENTHUB_EMAIL` / `EVENTHUB_PASSWORD` | eventhub.rahulshettyacademy.com test account (Web + API) |
| `LOGIN_USERNAME` / `LOGIN_PASSWORD` | Generic sample credentials for framework self-tests |
| `BROWSERSTACK_USERNAME` / `BROWSERSTACK_ACCESS_KEY` | Only when `mobile.device.provider=BROWSERSTACK` |

Any value `SecretManager.get(...)` resolves is auto-masked in every subsequent log/report
line. Masking itself is systemic, not per-call-site: `com.framework.secrets.MaskingMessageConverter`
(`%maskedMsg` in `logback.xml`, replacing the standard `%msg`) masks every CONSOLE/FILE line
unconditionally, and `ExtentLoggingAppender` does the same directly — a new `logger.info(...)`
anywhere that happens to touch a secret value is masked without anyone needing to remember
`.mask()` at that call site.

Masked output is `********-xxxxxxxx`, not a flat `********` — the suffix is a short
deterministic fingerprint of the real value (same secret → same suffix, different secret →
different suffix), so a report reader can tell "was the same email used three steps ago" or
"did this run pick up a different dataset row than expected" without the real value ever
appearing. Debugging a failure that needs the real value: `mvn test -Dmasking.enabled=false ...`
shows it in full — local-only, since a CI environment (`CI`/`GITHUB_ACTIONS` env vars) ignores
the flag unconditionally and always stays masked, so this can never leak into a shared report
regardless of how the build was invoked. `${{KEY}}` placeholders resolve the same values in test data:

```json
{ "validLogin": { "email": "${{EVENTHUB_EMAIL}}", "password": "${{EVENTHUB_PASSWORD}}" } }
```

**Key properties** (full list in `ConfigKeys`):

| Group | Keys |
|---|---|
| General | `env`, `browser`, `headless`, `resolution`, `base.url`, `api.base.url` |
| Web timeouts | `page.load.timeout`, `script.timeout`, `implicit.wait.timeout`, `explicit.wait.timeout`, `polling.interval` |
| Screenshots | `screenshot.mode` — `FAILURE` \| `EVERY_ACTION` \| `DISABLED` |
| API timeouts | `api.connection.timeout`, `api.socket.timeout` (ms) |
| Retry | `retry.max.count` (default 1; never retries `AssertionError`) |
| Test data | `testdata.format` — `json` (default) \| `yaml` \| `csv` \| `excel`, see [Test data](#test-data) |
| Mobile platform | `mobile.platform` — `android`/`ios`, picks a list in `config/mobile-devices.json` for a sequential run |
| Mobile app | `mobile.app.path.android`, `mobile.app.path.ios` — which app binary each platform installs |
| Mobile misc | `mobile.automation.name`, `mobile.udid`, `mobile.app.package`, `mobile.app.activity`, `mobile.bundle.id`, `appium.server.url` |
| Mobile provider | `mobile.device.provider` — `LOCAL` (default) \| `BROWSERSTACK` |
| BrowserStack | `browserstack.server.url`, `browserstack.app.id`, `browserstack.project.name`, `browserstack.build.name` |

## Test data

```java
TestDataManager.load("login.json").get("validLogin", AuthRequest.class);
TestDataManager.load("events.csv").dataProvider(CreateEventRequest.class);
```

| Format | Folder | Shape |
|---|---|---|
| JSON | `testdata/json/` | Object-root (named records) or array-root |
| YAML | `testdata/yaml/` | Same two shapes as JSON |
| CSV | `testdata/csv/` | Row-oriented; a `name` column enables name lookup |
| Excel | `testdata/excel/` | Same as CSV, first sheet |

Raw records are cached once; `${{...}}` placeholders resolve fresh on every access, so a
value produced mid-test (e.g. `${{eventId}}`) resolves correctly.

**Which format a bare name reads from is a config property, not hardcoded per call site.**
`load("login.json")` always reads JSON, extension and all — but `load("login")`, with no
extension, resolves against `testdata.format` in `config/{env}.properties` (`json` unless
set):

```properties
testdata.format=json   # or yaml | csv | excel
```

```java
TestDataManager.load("login");   // testdata.format=json -> testdata/json/login.json
                                  // testdata.format=yaml -> testdata/yaml/login.yaml
```

Override it per run the same way as any other key (`-Dtestdata.format=yaml`) or per test
(`ConfigManager.setOverride(ConfigKeys.TEST_DATA_FORMAT, "yaml")`) to switch every
extension-less `load(...)` call to a different source without touching test code.

## Running tests

```bash
mvn clean compile

# Default "everything green" run — excludes mobile (no local emulator by default) and
# frameworkSelfTest (a deliberately-always-failing test, see Troubleshooting)
mvn test -DexcludedGroups=mobile,frameworkSelfTest

mvn clean test -Denv=qa -Dgroups=smoke -Dbrowser=chrome -Dheadless=true
mvn test -Dtest=AuthenticationTest                              # one class
mvn test -Dtest=AuthenticationTest#loginWithExistingAccountWorks # one method
mvn test -Dtest=AuthenticationTest,EventBookingChainingTest      # several classes
mvn test -Dgroups=smoke,api                                      # several groups
mvn test -Dgroups=sanity -DexcludedGroups=mobile                 # one live test per surface
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4          # parallel
mvn test -Dgroups=frameworkSelfTest                               # proves retries never mask an assertion
```

Real groups this codebase tags tests with: `smoke`, `sanity`, `api`, `web`, `mobile`,
`frameworkSelfTest`. A group name nothing is tagged with matches zero tests but still reports
`BUILD SUCCESS` — check the printed test count.

**Rerunning only what failed:** Surefire's TestNG provider writes
`target/surefire-reports/testng-failed.xml` after every run — a suite file listing just the
classes/methods that failed, regardless of the fact that this repo has no suite XML of its own.
Feed it straight back in to rerun only those:

```bash
mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test
```

Confirmed live: after a run that failed one `MobileDriverFactoryTest` method, this reran
exactly that method and nothing else. Overwritten by the next full run, so grab a copy first
if you want to keep retrying a specific failure while iterating on other tests.

**API** — every real class: `AuthenticationTest`, `DataDrivenLoginTest`,
`DataDrivenEventCreationTest`, `EventBookingChainingTest`, `ApiContextChainingTest`.

```bash
mvn test -Dgroups=api                                              # every API test
mvn test -Dgroups=smoke,api                                        # just the smoke-tagged ones
mvn test -Dtest=AuthenticationTest                                 # one class
mvn test -Dtest=AuthenticationTest#loginWithExistingAccountWorks    # one method
mvn test -Dtest=AuthenticationTest,EventBookingChainingTest         # several classes
mvn test -Dtest=DataDrivenLoginTest                                 # data-driven - every @DataProvider row runs
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4            # parallel, one class per thread
mvn test -Dgroups=api -Dparallel=methods -DthreadCount=8            # parallel, one method per thread
mvn test -Denv=dev -Dgroups=api                                     # against dev instead of qa
```

**Web** — every real class: `LoginTest`, `EventsTest`.

```bash
mvn test -Dgroups=web                                               # every Web test
mvn test -Dgroups=smoke,web                                         # just the smoke-tagged ones
mvn test -Dtest=LoginTest                                           # one class
mvn test -Dtest=LoginTest#validLoginNavigatesToHomePage              # one method
mvn test -Dtest=LoginTest,EventsTest                                 # several classes
mvn test -Dtest=LoginTest#loginWorksAcrossMultipleBrowsers            # cross-browser data-driven, sequential rows
mvn test -Dgroups=web -Dbrowser=firefox -Dheadless=true               # browser: chrome (default) | firefox | edge | safari
mvn test -Dgroups=web -Dparallel=classes -DthreadCount=4 -Dheadless=true
mvn test -Denv=dev -Dgroups=web -Dbrowser=chrome -Dheadless=true      # against dev instead of qa
```

**Mobile** — every real class: `LoginTest`, `ProductsTest`, `MultiDeviceParallelTest`. Device
details are never passed on the CLI; whether it runs sequentially on one device or in
parallel across several depends only on whether `-Dparallel` is present:

```bash
mvn test -Dgroups=mobile                                              # sequential, one device (android by default)
mvn test -Dgroups=mobile -Dmobile.platform=ios                        # sequential, iOS instead - one line, no other flags
mvn test -Dgroups=mobile -Dtest=LoginTest                             # one class
mvn test -Dgroups=mobile -Dtest=LoginTest#validLoginNavigatesToProductsPage  # one method
mvn test -Dgroups=mobile -Dtest=LoginTest,ProductsTest                # several classes
mvn test -Dgroups=mobile -Dparallel=methods -DthreadCount=3           # pooled across every device (work queue)
mvn test -Dgroups=mobile -Dmobile.device.provider=BROWSERSTACK ...    # real device / cloud farm, see BrowserStack
mvn test -Dtest=MultiDeviceParallelTest#appLaunchesOnEachDeviceInTheMatrixConcurrently  # same test, every device at once
mvn test -Dtest=MultiDeviceParallelTest#appLaunchesOnEachIosSimulatorConcurrently
mvn test -Denv=dev -Dgroups=mobile                                    # against dev instead of qa
```

Every device the framework knows about lives in one shared file, `config/mobile-devices.json`
— not `config/{env}.properties`, and not duplicated per environment, since the same local
emulator/simulator is used regardless of `-Denv`:

```json
{
  "devices": {
    "android1": { "platform": "android", "deviceName": "Pixel_10", "platformVersion": "17" },
    "ios1": { "platform": "ios", "deviceName": "iPhone 17 Pro", "platformVersion": "26.2" },
    "ios2": { "platform": "ios", "deviceName": "iPhone 17", "platformVersion": "26.2" }
  },
  "androidList": ["android1"],
  "iosList": ["ios1", "ios2"],
  "matrices": { "cross-platform": ["android1", "ios1"], "ios": ["ios1", "ios2"] },
  "ports": { "systemPort": { "start": 8200, "count": 50 } }
}
```

- **Sequential** (`mvn test -Dgroups=mobile`, no `-Dparallel`): `mobile.platform` in
  `config/{env}.properties` (`android` or `ios`) picks `androidList`/`iosList`, and the first
  id in that list is used for every test. Switch platform with a one-line config edit — no
  `-D` device flags, no code change. (An explicit `-Dmobile.platform=...`/`-Dmobile.device.name=...`
  still overrides everything for a genuine one-off, but it's never required.)
- **Parallel** (`-Dparallel` present, any mode/thread count): every test is distributed across
  `androidList` + `iosList` combined as a work queue — whichever device finishes first picks
  up the next queued test, not "one device per thread regardless of load," and not "the same
  test on every device" (that's the `matrices` case below). Existing test classes
  (`LoginTest`, `ProductsTest`, ...) need no changes to participate.
- **Which app binary** each platform installs is an environment/build concern, so it's in
  `config/{env}.properties`, not the JSON file: `mobile.app.path.android`,
  `mobile.app.path.ios` — one entry per platform, since every device on that platform runs the
  same build.
- **Same test on every device at once** (a *matrix*, not a work queue — the last two commands
  in the list above): `matrices` is a comma-separated list of ids from the same `devices` map
  — a device used by more than one matrix is still declared only once.

Add a device, a new matrix, or point `androidList`/`iosList`/`mobile.platform` at a different
one, by editing `config/mobile-devices.json` (devices/lists/matrices) or
`config/{env}.properties` (`mobile.platform`) — no code to touch. See
`com.framework.driver.MobileDeviceMatrix`.

**Appium ports:** `appium.server.url` (`http://127.0.0.1:4723/wd/hub` by default) is the
*one* Appium server every `LOCAL` session — sequential, pooled, or matrix — connects through;
one HTTP port legitimately serving many concurrent sessions is normal, the same as any web
server. Each individual session then gets its own separate, per-session automation port on
top of that: `systemPort` for Android (UiAutomator2, from `8200`), `wdaLocalPort` for iOS
(XCUITest, from `8100`), and `chromedriverPort` for an Android device whose JSON entry sets
`"hybrid": true` (WebView/Chrome content, from `9515`). `MobilePortAllocator` checks a port
out of a bounded pool per driver creation and returns it when that driver is quit — success,
failure, or a retry — so concurrent sessions never collide and the pool never leaks across a
long run; every allocation/release is logged
(`logs/framework.log`, `MobilePortAllocator`). Pool sizes are configurable, with no code
change, via `config/mobile-devices.json`'s `ports` section (`start`/`count` per port type;
defaults shown above).

## Examples

**Web:**

```java
@Test(groups = {"smoke", "web"})
public void validLoginNavigatesToHomePage() {
    new LoginPage().open(ConfigManager.getBaseUrl())
            .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
            .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
            .clickLogin();

    assertTrue(new HomePage().isDisplayed());
}
```

Page Objects extend `BasePage`, expose business-level actions only, and log each step
(`logger.info(...)`), which mirrors automatically into the Extent report.

**Mobile:**

```java
@BeforeMethod(alwaysRun = true)
public void launchApp() { MobileDriverManager.getDriver(); }

@Test(groups = {"smoke", "mobile"})
public void standardUserCanLogIn() {
    new LoginPage().enterUsername(USERNAME).enterPassword(PASSWORD).tapLogin();
    assertTrue(new ProductsPage().isDisplayed());
}
```

Mirrors Web exactly (`BaseMobilePage`, W3C `PointerInput` gestures, not the deprecated
`TouchAction`). The same `test-*` accessibility identifiers work unmodified on Android and
iOS.

**API:**

```java
@Test(groups = {"smoke", "api"})
public void loginWithExistingAccountWorks() {
    AuthResponse response = authService.login(
            SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    assertTrue(response.success());
}
```

Services (`AuthenticationService`, `EventService`, `BookingService`) wrap `ApiClient` — the
only place REST Assured is called from. Every request/response is logged (masked) and
attached to the Allure report automatically.

**API chaining:**

```java
ApiResponse createEventResponse = eventService.createEvent(eventRequest);
ApiContext.set("eventId", String.valueOf(createEventResponse.jsonPath().getInt("data.id")));

CreateBookingRequest bookingRequest = new CreateBookingRequest(
        Integer.parseInt(ApiContext.get("eventId")), "Context Tester",
        "context.tester@example.com", "+91-9876500001", 1);
bookingService.createBooking(bookingRequest);
```

`ApiContext` (thread-local, backed by `VariableManager`) also self-registers as a
`PlaceholderResolver` source, so `${{eventId}}` resolves in test data too — same mechanism
covers `ApiClient`'s bearer token (`${{accessToken}}`).

## Parallel execution

```bash
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4
```

Plain Surefire/TestNG system properties — no suite XML needed. `-Dparallel=classes` runs
each class on its own thread; `-Dparallel=methods` parallelizes at the method level.

Mobile parallelizes the same way (`-Dparallel` present, any mode), distributing tests across
every configured device as a work queue instead of one shared device — see
[Running tests](#running-tests). Distinct `systemPort`/`wdaLocalPort`/`chromedriverPort` per
session are handled automatically. Genuine parallel mobile needs multiple emulators/devices/
BrowserStack capacity, since one local emulator only runs one session at a time.

## Reporting

- **Extent** (`reports/extent/index.html`, self-contained HTML) — every `logger.info(...)`
  in framework/test code mirrors into the report automatically via a Logback appender.
- **Allure** (`allure-results/`, raw JSON — `allure serve allure-results` to view) — masked
  API request/response and screenshots attached automatically.
- **Screenshots** — `screenshot.mode` = `FAILURE` (default) | `EVERY_ACTION` | `DISABLED`.
- **Retry** — `RetryAnalyzer` (`retry.max.count`, default 1) retries everything except
  `AssertionError`; a retried attempt's own report entry is kept, labeled `(Retry N)`.
- **Coverage** — `mvn test` also runs Jacoco (`jacoco-maven-plugin`, bound to the `test` phase
  itself, not `verify`, so plain `mvn test` is enough): `target/site/jacoco/index.html` for the
  human-readable report, and a `jacoco-check` execution that fails the build if `com.framework.*`
  line coverage drops below 55% — calibrated to a real measured `mvn test
  -DexcludedGroups=mobile,frameworkSelfTest -Dheadless=true` run (63.3% at the time this gate was
  added), not guessed. Raise the floor as real coverage grows; never lower it to make a
  regression pass.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) is the only CI config in this repo, running the
same `mvn` command shape you'd run locally:

| Job | Trigger | Command shape |
|---|---|---|
| `smoke` | Every pull request | Matrix over `chrome`/`firefox`: `-Denv=qa -Dgroups=smoke -Dbrowser=<matrix> -Dheadless=true -DexcludedGroups=mobile,frameworkSelfTest` |
| `regression` | Push to `main` | Same matrix, `-Dgroups=` (every group) |
| `regression-manual` | Manual dispatch | Single browser — `env`/`groups`/`browser` from the dispatch inputs (default `qa`/every group/`chrome`) |

`smoke`/`regression` run a real `chrome`+`firefox` matrix (`fail-fast: false`, so one browser's
failure doesn't cancel the other) — `ubuntu-latest` only guarantees Chrome and Firefox
preinstalled, not Edge/Safari, so those stay local/self-hosted-only for now. A manual dispatch
stays single-browser on purpose: it's normally someone deliberately targeting one specific
combination, not asking for the full sweep.

Every job archives `target/surefire-reports/`, `logs/`, `reports/extent/`, `allure-results/`,
`target/screenshots/`, and `target/site/jacoco/` regardless of pass/fail (per-browser artifact
names, since a matrix run can't share one name across its own legs).

Secrets: repo **Settings > Secrets and variables > Actions**, for `EVENTHUB_EMAIL` /
`EVENTHUB_PASSWORD` — any new key `SecretManager.get("KEY")` needs resolves against
`System.getenv("KEY")` automatically, no framework change required. `ubuntu-latest` ships
Chrome/Firefox preinstalled, so no driver-install step is needed.

## BrowserStack

The same Mobile test/Page Object code runs unchanged against BrowserStack — only
`mobile.device.provider` and a few config values change:

```bash
mvn test -Dgroups=mobile -Dmobile.device.provider=BROWSERSTACK \
    -Dmobile.device.name="Samsung Galaxy S23" -Dmobile.platform.version=13 \
    -Dbrowserstack.app.id=bs://<app-id-from-browserstack-upload>
```

1. Add `BROWSERSTACK_USERNAME` / `BROWSERSTACK_ACCESS_KEY` as a secret (never plain config).
2. Upload the app once per version via BrowserStack's own
   [app-upload API](https://www.browserstack.com/docs/app-automate/appium/upload-app) — not
   automated here; it returns the `bs://<id>` string `browserstack.app.id` needs.
3. `mobile.device.name`/`mobile.platform.version` are placed automatically under the correct
   BrowserStack capabilities by `DriverFactory` — same config keys as `LOCAL`.
4. Optional: `browserstack.project.name` / `browserstack.build.name` for dashboard grouping.

None of `config/{env}.properties` ship with `browserstack.*` pre-filled — pass them as `-D`
flags per run, or add persistent lines only if an environment should always target
BrowserStack.

## Writing tests

Test code calls only business-level Page Object/Service methods — never raw
Selenium/Appium/REST Assured. Retry, logging, reporting, and driver cleanup are automatic.

> **Every new `@BeforeMethod` needs `alwaysRun = true`.** Otherwise it silently stops running
> the moment `-Dgroups=` is added to a command.

**New page (Web/Mobile):** extend `BasePage`/`BaseMobilePage`, expose actions returning
`this` (chaining) or the next Page Object (real navigation). For a repeated element, extend
`BaseComponent`/`BaseMobileComponent` and take an already-located root element in the
constructor. Mobile gestures go through `MobileActions`'s W3C `PointerInput`, never
`TouchAction`.

**New API service:**

```java
public final class MyNewService {
    public ApiResponse doSomething(MyRequest request) {
        return ApiClient.execute(ApiRequest.post("/my-endpoint").body(request));
    }
}
```

Request/response DTOs are Java records under `api/requests`/`api/responses`; one service
method per endpoint, never calling REST Assured outside `ApiClient`.

## Thread safety

Every shared object in `com.framework.*` falls into one of five categories: **immutable
global** (loaded once, read-only), **thread-safe singleton** (`ConcurrentHashMap`,
`CopyOnWriteArrayList`, `AtomicInteger`), **thread-local**, **test-scoped** (safe only because
TestNG runs one thread per class instance), or **suite-scoped listener** (stateless, only
touches other classes' thread-local state).

Notably: `ConfigManager`'s per-test overrides, `VariableManager`/`ApiContext`, and every
WebDriver/AppiumDriver are `ThreadLocal`. `MobilePortAllocator` checks each port out of a
bounded, thread-safe pool and always returns it on driver quit — success, failure, or a retry
— rather than caching one per thread; an earlier cached-per-thread version caused real port
collisions on retry. Test class instance fields
written in `@BeforeMethod` and read in `@AfterMethod` are safe under `parallel="classes"` but
would **not** be safe under `parallel="methods"` on the same class — none of this framework's
tests run that way.

Validated live: `-Dparallel=classes -DthreadCount=4` with genuinely concurrent Chrome/Firefox
sessions and API calls (distinct thread names/overlapping timestamps in
`logs/framework.log`); `-Dparallel=methods` via `invocationCount`/`threadPoolSize` stress
tests in `ApiContextChainingTest`/`TestDataManagerTest`; and a real Android emulator + iOS
simulator launching concurrently in `MultiDeviceParallelTest`, confirmed via overlapping
`POST /session` requests in Appium's own server log.

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Plain `mvn test` fails on `RetryBehaviorTest` | Expected — `frameworkSelfTest` deliberately fails every run to prove retries never mask a real assertion. Run `-DexcludedGroups=mobile,frameworkSelfTest`. |
| Mobile fails with `SessionNotCreated` | No emulator/Appium server running — start both, or exclude `mobile`. |
| `-Dgroups=X` runs zero tests but still `BUILD SUCCESS` | `X` isn't a real group tag. Real ones: `smoke`, `sanity`, `api`, `web`, `mobile`, `frameworkSelfTest`. Check the printed test count. |
| `@BeforeMethod`-driven setup silently doesn't run under `-Dgroups=X`, test fails with 401 | Missing `alwaysRun = true` on that `@BeforeMethod`. |
| Masking looks missing on a new log/report line | Shouldn't happen — CONSOLE/FILE/Extent mask every line unconditionally now (see Reporting). If it does, that line went through some other sink entirely (e.g. a raw `System.out.println`, or a custom appender bypassing `logback.xml`'s pattern), not a missed `.mask()` call. |
| A report shows `********-xxxxxxxx` and you need the real value to debug a failure | `mvn test -Dmasking.enabled=false ...` — local only, ignored automatically in CI (see Configuration/Secrets). Two masked values with the same suffix are the same underlying secret, even without unmasking. |
| A `@DataProvider` row with a secret leaks into `allure-results/` | A separate pathway from logging — Allure's own interceptor bypasses the masker via `toString()`. Give the row type a custom masking `toString()`. |
| Web browser config looks wrong under `parallel="classes"` | `ConfigParameterListener` resets config before every invoked method — if this recurs, check nothing else caches a config value. |
| `Log4j2 could not find a logging implementation` | Harmless — Apache POI's internal logger falling back to SimpleLogger. |
| Selenium CDP version warnings | Harmless — Chrome's DevTools Protocol is newer than Selenium's bundled client. |
| Want to rerun just what failed, not the whole run | `mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test` — see [Running tests](#running-tests). |

**Where to look:** `logs/framework.log` (MDC-tagged per thread), `reports/extent/index.html`,
`allure-results/` (`allure serve allure-results`), `target/screenshots/`,
`target/surefire-reports/`.

**Debug from an IDE:** any test class/method is a plain TestNG entity — right-click > Debug
works directly. From the CLI: `mvn test -Dtest=... -Dmaven.surefire.debug`, then attach on
`localhost:5005`.
