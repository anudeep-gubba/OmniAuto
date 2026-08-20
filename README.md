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
src/main/java/com/framework/     <- core framework: generic, reusable, knows nothing about
                                     eventhub specifically. Ships as the framework's own
                                     compiled output; src/test never appears in it.
    api/            ApiClient, ApiRequest/Response, ApiContext, ApiHeaders - a generic REST
                    client engine. No knowledge of any specific endpoint/DTO shape.
    config/         ConfigManager (4-tier precedence)
    constants/      ConfigKeys
    context/        VariableManager (thread-safe runtime variable store)
    driver/         DriverFactory, WebDriverManager, MobileDriverManager, MobilePortAllocator
    enums/          BrowserType, Environment, MobilePlatformType, MobileDeviceProvider, ScreenshotMode
    exceptions/     FrameworkException and subtypes
    listeners/      TestNG listeners (see table below)
    mobile/         BaseMobilePage, BaseMobileComponent, MobileActions, MobileUtils, MobileWaits
                    - base classes only; no concrete screen lives here.
    reporting/      ExtentManager, ExtentLoggingAppender, AllureManager, ReportManager
    secrets/        SecretManager, SensitiveDataMasker
    testdata/       TestDataManager (incl. getCaseData), TestData, TestCaseMetadata, TestCaseRecord,
                    JSON/YAML/CSV/Excel readers, PlaceholderResolver
    utils/          JsonUtils, FileUtils, ScreenshotUtils, DateUtils, RandomDataUtils, EnumUtils,
                    Verify (drop-in org.testng.Assert replacement that reports each assertion)
    web/            BasePage, BaseComponent, WebActions, WebUtils, WebWaits - base classes only;
                    no concrete page lives here.

src/test/java/com/tests/         <- application-specific: everything that only makes sense
                                     because the app under test is eventhub - Web, Mobile, and
                                     API surfaces of the same product. Two top-level packages,
                                     nothing else: tests/ (every *Test.java spec, and only
                                     specs) and application/ (everything a spec needs to run -
                                     page objects, components, request/response DTOs, services,
                                     test-case data). A tester opening tests/ sees only specs to
                                     read/write; anything else lives in application/ instead.
    tests/
        api/          API test specs: AuthApiTest, EventApiTest, BookingApiTest, SystemApiTest,
                      EventBookingE2EFlowTest (positive/negative/E2E, live API, no mocks)
        mobile/       Mobile test specs: LoginTest, EventsTest, EventBookingE2EFlowTest
                      (positive/negative/E2E), MultiDeviceParallelTest (device-matrix infra,
                      app-agnostic) - eventhub's own Flutter app. Replaces an earlier suite
                      against the public Sauce Labs SwagLabs demo app (removed).
        web/          Web test specs: LoginTest, EventsTest (eventhub.rahulshettyacademy.com)
    application/
        base/             BaseApiTest/BaseMobileTest/BaseWebTest - the per-surface @BeforeMethod/
                          @AfterMethod lifecycle (login, driver/dialog setup, thread-state
                          cleanup) every spec in tests/ extends instead of re-declaring; see
                          "Writing tests" below.
        pages/web/        Web Page Objects (LoginPage, HomePage, EventsPage)
        pages/mobile/     Mobile Page Objects (LoginPage, HomePage, EventsPage, EventDetailPage,
                          BookingConfirmationPage, MyBookingsPage)
        components/web/   Web Components (HeaderComponent, EventCardComponent)
        components/mobile/ Mobile Components (HeaderComponent, EventCardComponent)
        requests/         eventhub-specific API request DTOs (Java records) - AuthRequest,
                          CreateEventRequest, CreateBookingRequest
        responses/        eventhub-specific API response DTOs (Java records) - AuthResponse,
                          EventResponse, BookingResponse, and the rest
        services/         AuthenticationService, EventService, BookingService, SystemService -
                          eventhub's own endpoints; wrap com.framework.api.ApiClient
        testdata/         Every surface's test-case data, gathered in one tree instead of
                          scattered per surface (see "Test data" below):
            TestDataSurface.java   every testdata/* file this repo reads (WEB/API/
                                    MOBILE_ANDROID/MOBILE_IOS), one place that knows each
                                    surface's bare file name - see "Test data" below
            LoginTestCase.java     email/password shape shared by Web and Mobile login (byte-
                                    for-byte identical across both, so one record instead of a
                                    near-duplicate WebLoginTestCase/MobileLoginTestCase pair) -
                                    read from web/web.json, android/android.json, and ios/ios.json
            api/          AuthApiTestCase, EventPayloadTestCase, BookingApiTestCase - each
                          file's *Data record (e.g. AuthApiTestCase.AuthApiData) is nested
                          inside it, so a pair is one file to create or update, not two.
                          AuthApiTestCase stays separate from LoginTestCase above despite the
                          field overlap - it also covers register/token cases LoginTestCase
                          doesn't model, and its data are raw HTTP request bodies, not UI form
                          fields
            mobile/       MobileBookingTestCase (same nested-Data convention; no Web
                          equivalent to share with)

config/             dev/qa.properties + mobile-devices.json — repo root, not
                    src/main/resources, so they're easy to find and edit; each
                    {env}.properties file is fully self-contained
apps/               Mobile binaries: eventhub-app-release.apk (Android, universal - installs on
                    both a real device and an x86_64 emulator) and eventhub-app-simulator.app
                    (iOS Simulator build - see "Mobile" below for why the simulator build
                    specifically, not just "the iOS app").
.github/workflows/  CI pipeline (GitHub Actions)
scripts/            clean-local.sh — removes accumulated local run artifacts (allure-results/,
                    allure-report/, test-output/, logs/, reports/; add --all for `mvn clean`
                    too) - all git-ignored and regenerated by the next `mvn test`, so safe to
                    run any time
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
| `AllureParameterMaskingListener` | Masks every Allure "Parameters" entry (`@DataProvider` rows included) before `allure-testng` writes the result to disk — closes the `toString()`-bypasses-the-masker gap |
| `BeforeMethodAlwaysRunListener` | Fails the suite at start-of-run if any `@BeforeMethod` has no `groups()` and no `alwaysRun = true` — turns the silent-skip-under-`-Dgroups=` footgun into a loud, actionable one |

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

**Masking is off by default; turn it on for a run you intend to share.** An ordinary local run
shows real values in logs/reports as-is — the common case is a developer's own `.secret.env`
already has the value, and reading it straight off the console/report while debugging is the
point. Enable masking for a given run with `-Dmasking.enabled=true` or a `MASKING_ENABLED=true`
environment variable (the `-D` flag wins if both are set):

```bash
mvn test -Dmasking.enabled=true ...     # or: MASKING_ENABLED=true mvn test ...
```

**CI cannot opt out of it** — a CI environment (`CI`/`GITHUB_ACTIONS` env vars) always masks,
regardless of the flag above, so a shared CI report/artifact can never go out unmasked no
matter how the build was invoked. When masking is on, any value `SecretManager.get(...)`
resolves is auto-masked in every subsequent log/report line. Masking itself is systemic, not
per-call-site: `com.framework.secrets.MaskingMessageConverter` (`%maskedMsg` in `logback.xml`,
replacing the standard `%msg`) masks every CONSOLE/FILE line unconditionally, and
`ExtentLoggingAppender`/`AllureParameterMaskingListener` do the same directly — a new
`logger.info(...)` anywhere that happens to touch a secret value, or a `@DataProvider` row
carrying one, is masked without anyone needing to remember `.mask()` at that call site.

Masked output is `********-xxxxxxxx`, not a flat `********` — the suffix is a short
deterministic fingerprint of the real value (same secret → same suffix, different secret →
different suffix), so a report reader can tell "was the same email used three steps ago" or
"did this run pick up a different dataset row than expected" without the real value ever
appearing. `${{KEY}}` placeholders resolve the same values in test data:

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
| Reporting | `report.overwrite` — `true` (default): every run replaces `reports/extent/index.html`. `false`: every run gets its own `reports/extent/report-{timestamp}.html`, so local runs stay side by side. `report.types` — comma-separated subset of `extent`\|`allure` (default `extent`), which report(s) this framework's own code enriches — see [Reporting](#reporting) |
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
TestDataManager.getCaseData("web/web", "validLogin", LoginTestCase.class); // file + case name -> data
TestDataSurface.WEB.getCaseData("validLogin", LoginTestCase.class);        // what a test method actually calls
```

**`getCaseData(fileName, caseName, caseType)` is what `TestDataSurface` calls under the hood** -
file name in, that case's `data` out, metadata logged automatically. No test class writes its
own `load(file).get(name, Type.class)` plus a hand-rolled logging line; that pairing lives once,
in `TestDataManager`, generic over any record implementing `com.framework.testdata.TestCaseRecord`
(a record shaped `(TestCaseMetadata metadata, D data)` satisfies it for free - see
`LoginTestCase`/`AuthApiTestCase`/etc.).

**`TestDataSurface` (`com.tests.application.testdata`) is what a test method actually calls** -
`WEB`/`API`/`MOBILE_ANDROID`/`MOBILE_IOS`, one value per surface, each knowing its own bare file
name so no test class hardcodes `"api/api"` (or worse, `"api/api.json"` - a hardcoded extension
silently locks that call site to JSON no matter what `testdata.format` says, since
`TestDataManager` only fills in a format-derived extension when the name doesn't already have
one). A mobile test that wants "whichever platform this run is actually driving" (the normal
case) calls `TestDataSurface.currentMobile()` rather than picking `MOBILE_ANDROID`/`MOBILE_IOS`
itself, so it never has to match whatever `-Dmobile.platform` the run was launched with. A test
file has nothing left to implement here beyond the one call.

| Format | Folder | Shape |
|---|---|---|
| JSON | `testdata/json/` | Object-root (named records) or array-root |
| YAML | `testdata/yaml/` | Same two shapes as JSON |
| CSV | `testdata/csv/` | Row-oriented; a `name` column enables name lookup |
| Excel | `testdata/excel/` | Same as CSV, first sheet |

**CSV/Excel carry the same nested `metadata`/`data` shape as JSON/YAML via dotted columns** -
a row-oriented format has no native nesting, so `metadata.testCaseId`/`data.email`-style column
names expand into the same nested map JSON/YAML produce (`TestDataReader.unflatten`), which is
what makes a CSV/Excel row convertible into a `*TestCase` record via `getCaseData` exactly like
any other format:

```csv
name,metadata.testCaseId,metadata.testCaseName,data.email,data.password
validLogin,TC-WEB-LOGIN-001,User logs in successfully...,${{EVENTHUB_EMAIL}},${{EVENTHUB_PASSWORD}}
```

**A blank CSV/Excel cell means "this field is unset"** - the same as a JSON/YAML record simply
omitting the key - not an explicit empty string, since a row-oriented format has no way to omit
a column on just one row (e.g. `EventPayloadTestCase.EventPayloadData#eventDate` is left blank
on every case except the one that specifically needs a fixed literal date; code branches on
`eventDate() != null`). A case that genuinely needs an intentional empty-string value should
stay in JSON/YAML, where absent-vs-empty-string is representable directly.

`web/`, `api/`, `android/`, `ios/` each have all four formats present today (`testdata/{json,yaml,csv,excel}/web/web.*`,
etc.) - same case names, same values, mechanically derived from the JSON originals rather than
hand-retyped, so there is zero drift between them. Whichever one `testdata.format` (or an
explicit extension) picks, every test reads the identical data.

Raw records are cached once; `${{...}}` placeholders resolve fresh on every access, so a
value produced mid-test (e.g. `${{eventId}}`) resolves correctly.

**One folder, one file, per surface, never shared.** `testdata/json/api/api.json` backs the API
suite, `testdata/json/web/web.json` backs the Web suite, and `testdata/json/android/android.json`/
`testdata/json/ios/ios.json` back the Mobile suite (one platform-specific folder/file each,
resolved at runtime via `TestDataSurface.currentMobile()` off `mobile.platform`) -
each surface gets its own folder (not just a bare file) so more files can be added per surface
later without restructuring, even though every surface ultimately logs into the same eventhub
account; separate files mean a change made for one surface (e.g. a new Mobile-only negative
case) never risks a Web or API test picking up an unrelated row.

Every row in every one of these files splits `metadata` from `data`: `testCaseId`/`testCaseName`
identify the row (a readable business/scenario name, not a restatement of the Java method name)
for a failure, an Extent/Allure report line, or someone skimming the JSON - alongside `data`, the
actual values the test acts on (e.g. `com.tests.application.testdata.LoginTestCase`, `com.tests
.application.testdata.mobile.MobileBookingTestCase`, `com.tests.application.testdata.api.AuthApiTestCase`/
`EventPayloadTestCase`/`BookingApiTestCase`). Every `*TestCase` record pairs a shared
`com.framework.testdata.TestCaseMetadata` with a `*Data` record nested right inside it (e.g.
`AuthApiTestCase.AuthApiData`) - one file per pair, not two, so adding a field to an existing
shape is one file to touch - and implements `com.framework.testdata.TestCaseRecord<D>` (its own
generated `metadata()`/`data()` accessors satisfy it for free), which is what makes it usable
with `getCaseData` above. **Adding a brand-new test case never needs a new Java file at all** -
just a new JSON entry under an existing shape; a new record shape (a new test class's first
case, or a shape no other surface already shares) is the only time a new `testdata/` file is
needed - `LoginTestCase` itself is one record read from three different JSON files
(`web/web.json`, `android/android.json`, `ios/ios.json`) precisely because Web and Mobile's
login shape is identical, not because the files are shared.

```json
"malformedEmail": {
  "metadata": {
    "testCaseId": "TC-AND-LOGIN-004",
    "testCaseName": "Submitting a malformed email shows an invalid-email validation error"
  },
  "data": {
    "email": "not-an-email",
    "password": "SomePassword1!"
  }
}
```

**Which format a bare name reads from is a config property, not hardcoded per call site.**
A name with a recognized extension always reads that format, extension and all — but a bare
name with no extension resolves against `testdata.format` in `config/{env}.properties` (`json`
unless set):

```properties
testdata.format=json   # or yaml | csv | excel
```

```java
TestDataManager.load("web/web");   // testdata.format=json -> testdata/json/web/web.json
                                    // testdata.format=yaml -> testdata/yaml/web/web.yaml
```

Override it per run the same way as any other key (`-Dtestdata.format=yaml`) or per test
(`ConfigManager.setOverride(ConfigKeys.TEST_DATA_FORMAT, "yaml")`) to switch every
extension-less `load(...)` call to a different source without touching test code.

## Running tests

```bash
mvn clean compile

# Default "everything green" run — excludes mobile (no local emulator by default)
mvn test -DexcludedGroups=mobile

mvn clean test -Denv=qa -Dgroups=smoke -Dbrowser=chrome -Dheadless=true
mvn test -Dtest=AuthApiTest                                      # one class
mvn test -Dtest=AuthApiTest#loginWithExistingAccountWorks        # one method
mvn test -Dtest=AuthApiTest,EventBookingE2EFlowTest               # several classes
mvn test -Dgroups=smoke,api                                      # several groups
mvn test -Dgroups=sanity -DexcludedGroups=mobile                 # one live test per surface
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4          # parallel
```

Real groups this codebase tags tests with, four independent axes combinable in any filter:

| Axis | Values | Meaning |
|---|---|---|
| Run tier | `smoke`, `sanity` | `smoke` = broad, every PR; `sanity` = one narrowest "is anything even up" check per surface — see [CI/CD](#cicd) |
| Surface | `api`, `web`, `mobile` | which stack drives the test |
| Test shape | `positive`, `negative`, `e2e` | `positive`/`negative` = single-endpoint/screen happy-path vs. rejection case; `e2e` = a multi-step cross-resource journey (never combined with positive/negative - it's its own shape) |
| Resource | `auth`, `events`, `bookings`, `system` | which domain the test covers - an `e2e` test carries every resource its journey touches (e.g. both `events` and `bookings`) |

```bash
mvn test -Dgroups=negative                          # every rejection/validation case, any surface
mvn test -Dgroups=events -DexcludedGroups=mobile     # every events-domain test, Web+API
mvn test -Dgroups=e2e                                # every multi-step journey, any surface
mvn test -Dgroups=bookings,negative                  # booking rejection cases specifically
```

A group name nothing is tagged with matches zero tests but still reports `BUILD SUCCESS` —
check the printed test count. `MultiDeviceParallelTest` (device-matrix infra, app-agnostic - see
"Mobile" below) deliberately carries none of the test-shape/resource tags above: it isn't
verifying product behavior, so `positive`/`negative`/a resource tag would misrepresent what it
actually checks.

**Rerunning only what failed:** Surefire's TestNG provider writes
`target/surefire-reports/testng-failed.xml` after every run — a suite file listing just the
classes/methods that failed, regardless of the fact that this repo has no suite XML of its own.
Feed it straight back in to rerun only those:

```bash
mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test
```

Confirmed live: after a run with one failing method, this reran exactly that method and
nothing else. Overwritten by the next full run, so grab a copy first if you want to keep
retrying a specific failure while iterating on other tests.

**API** — every real class: `AuthApiTest`, `EventApiTest`, `BookingApiTest`, `SystemApiTest`,
`EventBookingE2EFlowTest`.

```bash
mvn test -Dgroups=api                                              # every API test
mvn test -Dgroups=smoke,api                                        # just the smoke-tagged ones
mvn test -Dtest=AuthApiTest                                        # one class
mvn test -Dtest=AuthApiTest#loginWithExistingAccountWorks           # one method
mvn test -Dtest=AuthApiTest,EventBookingE2EFlowTest                 # several classes
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
mvn test -Dgroups=web -Dbrowser=firefox -Dheadless=true               # browser: chrome (default) | firefox | edge | safari
                                                                       # (cross-browser coverage is CI's job matrix, not a per-test loop - see CI/CD)
mvn test -Dgroups=web -Dparallel=classes -DthreadCount=4 -Dheadless=true
mvn test -Denv=dev -Dgroups=web -Dbrowser=chrome -Dheadless=true      # against dev instead of qa
```

**Mobile** — every real class: `LoginTest`, `EventsTest`, `EventBookingE2EFlowTest`,
`MultiDeviceParallelTest` (device-matrix infra, app-agnostic). eventhub's own Flutter app
(replaces an earlier suite against the public Sauce Labs SwagLabs demo app, removed) - login,
browse/search events, and a full login→book→confirm→My Bookings journey, each verified live
against a real iPhone 17 Pro/iPhone 17 Simulator session before being committed (Appium +
XCUITest, real page source, not guessed locators). Device details are never passed on the
CLI; whether it runs sequentially on one device or in parallel across several depends only on
whether `-Dparallel` is present:

```bash
mvn test -Dgroups=mobile                                              # sequential, one device (android by default)
mvn test -Dgroups=mobile -Dmobile.platform=ios                        # sequential, iOS instead - one line, no other flags
mvn test -Dgroups=mobile -Dtest=LoginTest                             # one class
mvn test -Dgroups=mobile -Dtest=LoginTest#validCredentialsLogInAndShowHomeScreen  # one method
mvn test -Dgroups=mobile -Dtest=LoginTest,EventsTest,EventBookingE2EFlowTest  # several classes
mvn test -Dgroups=mobile -Dparallel=methods -DthreadCount=3           # pooled across every device (work queue)
mvn test -Dgroups=mobile -Dmobile.device.provider=BROWSERSTACK ...    # real device / cloud farm, see BrowserStack
mvn test -Dtest=MultiDeviceParallelTest#appLaunchesOnEachDeviceInTheMatrixConcurrently  # same test, every device at once
mvn test -Dtest=MultiDeviceParallelTest#appLaunchesOnEachIosSimulatorConcurrently
mvn test -Denv=dev -Dgroups=mobile                                    # against dev instead of qa
```

**iOS needs a Simulator-targeted build specifically, not just "the iOS app".** A real-device
`.ipa`/`.app` (built for the `iphoneos` SDK) cannot install on a Simulator regardless of
Appium config - verified live, the exact failure is `Simulator architecture is not supported
by the <bundle-id> application`. `apps/eventhub-app-simulator.app` is a Simulator build
specifically (`iphonesimulator` SDK, a universal `x86_64`/`arm64` binary - confirm with
`lipo -info`/`file` if a future rebuild ever needs re-checking). Android has no such split:
`apps/eventhub-app-release.apk` bundles an `x86_64` slice alongside the device ABIs, so the
one file installs on both a real device and an emulator.

This build's login always succeeds regardless of the password typed - verified live, its own
mock backend authenticates any well-formed credentials as one fixed demo account - so
`LoginTest`'s negative cases are the client-side form validation Flutter itself enforces
(blank fields, a malformed email), not a server-rejected wrong password; see `LoginPage`'s
class javadoc.

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
  (`LoginTest`, `EventsTest`, ...) need no changes to participate.
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
(`logger.info(...)`), which mirrors automatically into the Extent report. `assertTrue` here is
`com.framework.utils.Verify`'s, not `org.testng.Assert`'s directly — same signature (drop-in,
just a different static import), but logs its own PASS/FAIL step to both reports as it runs
instead of being invisible until the test's final summary. See [Reporting](#reporting).

**Mobile:**

```java
@BeforeMethod(alwaysRun = true)
public void launchApp() { MobileDriverManager.getDriver(); }

@Test(groups = {"smoke", "sanity", "mobile"})
public void validCredentialsLogInAndShowHomeScreen() {
    new LoginPage().enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
            .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
            .tapSignIn();
    assertTrue(new HomePage().isDisplayed());
}
```

Mirrors Web exactly (`BaseMobilePage`, W3C `PointerInput` gestures, not the deprecated
`TouchAction`). Locators are the Flutter app's own Semantics labels - the same value Appium
exposes as `accessibilityId` on iOS (`content-desc` on Android, since one Flutter Semantics
tree drives both) - rather than dedicated `test-*` ids (the earlier SwagLabs app's own
convention, not something this codebase controls).

**API:**

```java
@Test(groups = {"smoke", "api"})
public void loginWithExistingAccountWorks() {
    AuthResponse response = authService.login(
            SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    assertTrue(response.success());
}
```

Application-specific services (`com.tests.application.services` — `AuthenticationService`,
`EventService`, `BookingService`, `SystemService`) wrap `com.framework.api.ApiClient`, the only
place REST Assured is called from. Every request/response is logged (masked when enabled — see
[Secrets](#configuration)) and attached to both the Allure and Extent reports automatically.

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

**Which report(s) get this framework's own enrichment is configurable** —
`report.types` (default `extent`; `allure`, or `extent,allure` — see
[Configuration](#configuration)). Extent, after the work below, is the fully-enriched,
actively-used report; Allure is comparatively thin outside the API surface (no bridge for
business-narrative log lines, no assertion detail - see its own bullet below) and its one real
differentiator, cross-run trend/history dashboards, isn't wired up in this project's CI today.
Doing that enrichment work (masking, formatting, attaching) has a real cost even when nobody
opens the result, so `report.types` lets a run skip it outright rather than do it and discard
it - `ReportManager#isExtentEnabled`/`isAllureEnabled` gate every enrichment call site.
**`allure-testng`'s own native pass/fail/`@Before`/`@After` capture always runs regardless** -
it auto-registers itself via its own `META-INF/services` entry the moment it's on the classpath,
which can't be silenced from a runtime flag (only a build-time Maven profile excluding the
dependency entirely could do that) - `report.types=extent` only skips this framework's own
*added* detail on top of that bare capture, not `allure-results/` entirely.

- **Extent** (`reports/extent/index.html` by default, or a timestamped file per run if
  `report.overwrite=false` — see [Configuration](#configuration)) — every `logger.info(...)`
  in the business-narrative layer (Web/Mobile Page Objects and Components, API Services)
  mirrors into the report automatically via a Logback appender. The test title itself is a
  humanized version of the method name (`BookingApiTest — Booking Without Auth Returns 401`,
  not the raw `bookingWithoutAuthReturns401`) — see `ExtentReportingListener`.
- **API request/response detail in Extent** — a step header (`POST /events`), the masked
  request headers/body, and the response status/body, each rendered as its own scrollable,
  whitespace-preserved code block (`ExtentManager.logCodeBlock`) rather than a squashed single
  line. This is a deliberate *explicit* call from `ApiClient`, not the generic Logback mirror
  above: Extent's Spark theme renders a log line inside a plain HTML `<td>` with no
  `white-space: pre` of its own, so even an already pretty-printed multi-line body would still
  collapse to one unreadable line if it went through the ordinary text-mirror path (verified
  live against the generated report's own markup). `com.framework.api` is deliberately *not*
  wired to the `EXTENT` Logback appender for this reason.
  **Color-coded by status** — the response status line and body block render as a green badge
  for 2xx, amber for 3xx, red for 4xx/5xx (`ExtentManager#logStatusLine`/`colorForStatus`), the
  same at-a-glance signal as an `assertStatusCode` PASS/FAIL step, but shown for every call
  regardless of whether the test goes on to assert anything about it. This (and every Pass/Fail
  badge Extent renders natively) depends on a stylesheet the report's own HTML already loads
  from a CDN — not a new dependency introduced here, and the report's actual content (the code
  blocks above) never depends on it, only the color.
- **Assertion detail in Extent/Allure** — every assertion logs its own PASS/FAIL step inline,
  where it happened, not just the test's final summary: `com.framework.utils.Verify` (a
  drop-in `org.testng.Assert.assertTrue`/`assertFalse`/`assertEquals`/`assertNotNull`
  replacement — same signatures, swap the static import) for test-code assertions, and
  `ApiResponse#assertStatusCode` (the most-called check in this codebase) does the same
  directly. Neither changes assertion semantics — both still delegate to (or throw exactly
  like) the real thing; they only add the missing report step around it.
- **Allure** (`allure-results/`, raw JSON — `allure serve allure-results` to view), when
  `report.types` includes `allure`. With `allure` excluded, `allure-results/` still gets
  `allure-testng`'s own bare pass/fail entries (see above), just none of this:
  - **Steps** — the same business-narrative log lines Extent's bridge mirrors (Web/Mobile Page
    Objects and Components, API Services) also reach Allure as steps (`AllureLoggingAppender`,
    wired to the same loggers as `ExtentLoggingAppender`) - closes what used to be a documented
    gap ("Allure has no bridge for arbitrary log lines"). Each API call gets its own richer,
    explicit step instead (`ApiClient`/`Allure.step`), with everything below nested under it.
  - **API** — HTTP method, resolved request URL, request/response headers, query/path params,
    request/response body, response status code, response time (ms) - all masked, all attached
    or added to the test's Parameters table automatically. (Known trade-off for a test making
    several calls: `Allure.parameter(...)` is test-level, not step-scoped, so a later call's
    HTTP Method/Request URL/Response Time overwrites an earlier call's entry there - the
    request/response body/header *attachments* don't have this problem, they nest correctly
    under whichever step made them, see `ApiClient#logRequest`'s own javadoc.)
  - **Web** — screenshot, current URL, browser + version, and masked page source, all attached
    on failure (`ScreenshotCaptureListener`, same correctly-ordered "driver still alive" window
    the screenshot itself already relied on).
  - **Mobile** — screenshot, device name, platform + version, current activity (Android only -
    no iOS equivalent, best-effort), and masked page source, all attached on failure (same
    listener/window as Web).
  - **Feature/Story/Severity/Platform labels** and an **Environment** widget
    (`allure-results/environment.properties`) - all derived automatically from data this
    framework already has (the `@Test(groups=...)` taxonomy, `ConfigManager`) - see
    `AllureMetadataListener`. Retry grouping and parallel/timeline data are native to
    `allure-testng` already; nothing extra was needed for either.
- **Test case metadata** — every `TestDataManager.getCaseData(...)` call (i.e. every test data
  load) logs its `testCaseId`/`testCaseName` (reaching Extent for free via the Logback bridge
  above) and attaches them as Allure parameters (`AllureManager.attachTestCaseMetadata`) - so a
  failure in either report is immediately traceable to the exact named test case (and its
  file/row), filterable/sortable in Allure, without a test author adding any reporting code.
- **Screenshots** — `screenshot.mode` = `FAILURE` (default) | `EVERY_ACTION` | `DISABLED`.
- **Retry** — `RetryAnalyzer` (`retry.max.count`, default 1) retries everything except
  `AssertionError`; a retried attempt's own report entry is kept, labeled `(Retry N)`.
- **Coverage** — `mvn test` also runs Jacoco (`jacoco-maven-plugin`, bound to the `test` phase
  itself, not `verify`, so plain `mvn test` is enough): `target/site/jacoco/index.html` for a
  human-readable `com.framework.*` line-coverage report. Not gated - the `com.tests.base`
  framework self-test suite this used to be checked against was removed, dropping measured
  coverage well below any threshold worth enforcing without a real suite behind it; the report
  is still generated for visibility. Re-add a `jacoco-check` execution with a calibrated minimum
  if framework-level self-tests come back.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) is the only CI config in this repo, running the
same `mvn` command shape you'd run locally:

| Job | Trigger | Command shape |
|---|---|---|
| `smoke` | Every pull request | Matrix over `chrome`/`firefox`: `-Denv=qa -Dgroups=smoke -Dbrowser=<matrix> -Dheadless=true -DexcludedGroups=mobile` |
| `regression` | Push to `main` | Same matrix, `-Dgroups=` (every group) |
| `regression-manual` | Manual dispatch | Single browser — `env`/`groups`/`browser` from the dispatch inputs (default `qa`/every group/`chrome`) |

`smoke`/`regression` run a real `chrome`+`firefox` matrix (`fail-fast: false`, so one browser's
failure doesn't cancel the other) — `ubuntu-latest` only guarantees Chrome and Firefox
preinstalled, not Edge/Safari, so those stay local/self-hosted-only for now. A manual dispatch
stays single-browser on purpose: it's normally someone deliberately targeting one specific
combination, not asking for the full sweep.

Every job archives `target/surefire-reports/`, `logs/`, `reports/extent/`, `allure-results/`,
`target/screenshots/`, and `target/site/jacoco/` regardless of pass/fail (per-browser artifact
names, since a matrix run can't share one name across its own legs). None of these jobs pass
`-Dreport.types=...`, so they get the default (`extent` only, see [Reporting](#reporting)) -
the archived `allure-results/` is `allure-testng`'s own bare native capture, not this
framework's enrichment; add `-Dreport.types=extent,allure` to a job's command if a downstream
Allure dashboard is ever wired up to consume these artifacts.

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

> **Every new `@BeforeMethod` needs `alwaysRun = true`.** `BeforeMethodAlwaysRunListener` now
> fails the suite at start-of-run with the exact `Class#method` if one is missing it, rather
> than letting it silently stop running the moment `-Dgroups=` is added to a command - still
> worth getting right the first time, but no longer a silent trap if you don't.

**New test class:** extend `BaseApiTest`/`BaseMobileTest`/`BaseWebTest`
(`com.tests.application.base`) — never hand-write a login/logout or thread-state-cleanup
`@BeforeMethod`/`@AfterMethod`, that's already handled once per surface in the base class. A
class only writes its own `@BeforeMethod` when it needs a specific *starting state* (e.g.
`loginWithSeededAccount()` then navigate somewhere, or `ensureLoggedOut()` for a spec that tests
login itself); teardown of anything the class itself creates (a booking, an event) goes in an
overridden `tearDownTestData()`, not a new `@AfterMethod` — the base class runs it before
logout/cleanup automatically.

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
collisions on retry.

**Test class instance fields are the one place this bites easily, and did.** TestNG runs every
`@Test` method of a class on one shared instance under `parallel="methods"` — not one instance
per thread/method — so a plain instance field a test writes in its own body and reads back
later (typically to hand to `tearDownTestData()`) is exactly as unsafe as any other shared
mutable state without `ThreadLocal`. Audit finding, verified live: `mvn test -Dgroups=api
-Dparallel=methods -DthreadCount=8` (the exact command this README recommends above) reliably
reproduced two failures — `EventApiTest.gettingAnExistingEventByIdReturnsIt` and
`EventBookingE2EFlowTest.fullEventLifecycleFromRegistrationThroughBookingToDeletionWorksEndToEnd`
— where one thread's `createdEventId`/`createdBookingId` write was clobbered by a different
`@Test` method running concurrently on the same class instance, before the first thread read
it back. Beyond the flaky assertion, this had a worse silent failure mode: `tearDownTestData()`
reads the same field after the method returns, so a lost write could make it cancel/delete a
*different* thread's still-in-use booking/event instead of its own. `EventApiTest`,
`BookingApiTest`, and `EventBookingE2EFlowTest` (the only classes with this pattern) now hold
`createdEventId`/`createdBookingId` as `ThreadLocal<Integer>` instead of a plain field, same
convention as `ApiContext`/`ConfigManager` above — confirmed clean across several repeated
`-Dparallel=methods -DthreadCount=8` runs after the fix, where it failed nearly every run
before.

Validated live: `-Dparallel=classes -DthreadCount=4` with genuinely concurrent Chrome/Firefox
sessions and API calls (distinct thread names/overlapping timestamps in
`logs/framework.log`) across the `com.tests.tests.api` classes; and a real Android emulator + iOS
simulator launching concurrently in `MultiDeviceParallelTest`, confirmed via overlapping
`POST /session` requests in Appium's own server log.

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Mobile fails with `SessionNotCreated` | No emulator/Appium server running — start both, or exclude `mobile`. |
| `-Dgroups=X` runs zero tests but still `BUILD SUCCESS` | `X` isn't a real group tag — see the [group taxonomy table](#running-tests) (`smoke`/`sanity`, `api`/`web`/`mobile`, `positive`/`negative`/`e2e`, `auth`/`events`/`bookings`/`system`). Check the printed test count. |
| `@BeforeMethod` missing `alwaysRun = true` under `-Dgroups=X` | Can't happen silently any more — `BeforeMethodAlwaysRunListener` fails the suite at start-of-run with the exact `Class#method` if this is ever missing (see Listeners). |
| A report shows secret values in plain text | Expected by default — masking is off unless enabled (see Secrets). Turn it on for a run you intend to share: `mvn test -Dmasking.enabled=true ...` (or `MASKING_ENABLED=true`). CI always masks regardless of this flag. |
| Masking looks missing on a new log/report line despite `-Dmasking.enabled=true` | Shouldn't happen when masking is on — CONSOLE/FILE/Extent/Allure parameters all mask every line/value unconditionally once enabled (see Reporting). If it does, that line went through some other sink entirely (e.g. a raw `System.out.println`, or a custom appender bypassing `logback.xml`'s pattern), not a missed `.mask()` call. |
| Two masked values, want to know if they're the same secret | Compare the `********-xxxxxxxx` suffix — same secret always produces the same fingerprint. |
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
