# OmniAuto — Web-Mobile-API Automation Framework

One framework covering **Web** (Selenium), **Mobile** (Appium), and **API** (REST Assured) —
shared configuration, secrets, driver/context management, test data, logging, and reporting.
Every test is a **Gherkin/BDD scenario** (Cucumber), driven through TestNG via `cucumber-testng`
so retry/parallel/reporting stay TestNG-native under the hood — command-line flags throughout,
no suite XML anywhere.

**Stack:** JDK 17 · Maven · TestNG 7.10 · Cucumber 7.20 (`cucumber-testng`) · Selenium 4.25 ·
Appium 9.3 · REST Assured 5.5 · Extent 5.1 + Allure 2.29 · Logback

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

- **Every test is BDD.** `.feature` files (`src/test/resources/features/**`) hold the actual
  specs in Gherkin; `com.tests.steps.*` step-definition classes are the only place calling into
  page objects/services. There is no plain `@Test` method anywhere in `com.tests`.
- **Thread-safety is load-bearing.** Every shared object is either immutable, a
  concurrent-safe structure, or `ThreadLocal` — see [Thread safety](#thread-safety).
- **One placeholder syntax everywhere.** `${{KEY}}` resolves against secrets, config, and
  runtime/API context through a single `PlaceholderResolver`, in test data, request bodies,
  anywhere text is resolved.
- **No suite XML.** Runner, feature file, scenario name, tag expression, browser, environment,
  parallel mode — all plain `-D` flags. Picking a different subset is never a file edit.
- **Zero boilerplate per test.** Retry, log-tagging, Extent/Allure reporting, and driver
  cleanup are automatic via TestNG listeners (Cucumber scenarios run as ordinary TestNG
  invocations under `cucumber-testng`, so every one of these still applies unchanged).

**Known limitations:**

- **Mobile needs local infra** (emulator/simulator + Appium server) — not available on a
  hosted CI runner, so `@mobile` is excluded from CI by default. Use a self-hosted runner or
  BrowserStack.
- **BrowserStack app upload is manual** — one-time per app version, via their own API.
- **An empty `-Dcucumber.filter.tags="@x"` match still reports `BUILD SUCCESS`** — always check
  the printed test count.

## Architecture

```
                 GHERKIN FEATURES (.feature)
                          |
                   STEP DEFINITIONS
                  (com.tests.steps.*)
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
              TestNG (via cucumber-testng)
              Parallel Execution · Retry · Listeners
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
    reporting/      ExtentManager, ExtentLoggingAppender, AllureManager, ReportManager (Web/
                    Mobile) - plus ApiReportRecorder/ApiReportModel/ApiHtmlReportRenderer, the
                    API surface's own separate, dependency-free report (see Reporting below)
    secrets/        SecretManager, SensitiveDataMasker
    testdata/       TestDataManager (incl. getCaseData), TestData, TestCaseMetadata, TestCaseRecord,
                    JSON/YAML/CSV/Excel readers, PlaceholderResolver
    utils/          JsonUtils, FileUtils, ScreenshotUtils, DateUtils, RandomDataUtils, EnumUtils,
                    Verify (drop-in org.testng.Assert replacement that reports each assertion)
    web/            BasePage, BaseComponent, WebActions, WebUtils, WebWaits - base classes only;
                    no concrete page lives here.

src/test/resources/features/     <- the actual specs, in Gherkin - the only place test *behavior*
                                     is described. One directory per surface:
    web/            login.feature, events.feature
    api/            auth.feature, events.feature, bookings.feature, system.feature,
                    booking_e2e_flow.feature
    mobile/         login.feature, events.feature, booking_e2e_flow.feature

src/test/java/com/tests/         <- everything a .feature scenario needs to run, plus the
                                     application-specific layer underneath it (page objects,
                                     components, request/response DTOs, services, test-case
                                     data) - because the app under test is eventhub, Web/Mobile/
                                     API surfaces of the same product. A reader opening
                                     features/ sees only specs to read/write; every "how" lives
                                     here instead.
    runners/          One class, RunCucumberTest, the actual entry point Surefire's TestNG
                      discovery picks up (no suite XML, same as every class before this
                      migration) - runs every feature under features/** (Web/API/Mobile
                      together); surface selection is purely -Dcucumber.filter.tags, never a
                      class name. Named RunCucumberTest specifically, not *Runner - see its own
                      javadoc for why Surefire's default discovery needs that.
    steps/            Step definitions, `@Given`/`@When`/`@Then` methods calling into
                      page objects/services - the only code a .feature scenario invokes.
        web/          LoginSteps, EventsSteps
        api/          AuthSteps, EventSteps, BookingSteps, SystemSteps, BookingE2EFlowSteps,
                      CommonApiSteps (the handful of steps genuinely shared across more than
                      one API feature - see its own javadoc for why that's kept to one place)
        mobile/       LoginSteps, EventsSteps, BookingE2EFlowSteps
        shared/       One scenario-scoped context class per surface (WebScenarioContext,
                      ApiScenarioContext, MobileScenarioContext) - constructor-injected via
                      `cucumber-picocontainer` into every step-definition/hook class that needs
                      it within one scenario, so they share state (page objects, services,
                      "what to clean up") without inheriting from a common base class. This is
                      the composition-based replacement for the pre-BDD `Base*Test` classes.
    hooks/            WebHooks/ApiHooks/MobileHooks - Cucumber `@Before("@tag")`/`@After("@tag")`
                      hooks scoped to a surface's tag, doing what that surface's old `Base*Test`
                      `@BeforeMethod`/`@AfterMethod` used to (thread-state cleanup, releasing
                      test data a scenario created).
    application/
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
            api/          AuthApiTestCase, EventPayloadTestCase, BookingApiTestCase (consumed by
                          steps/api/AuthSteps, EventSteps, BookingSteps respectively) - each
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
| `ConfigurationRetryListener` | Extends the same `retry.max.count` policy to `@BeforeMethod`/`@AfterMethod` — TestNG has no `retryAnalyzer` concept for configuration methods |
| `ConfigParameterListener` | Bridges TestNG `<parameter>`s into `ConfigManager`, reset before every method |
| `ApiContextListener` | Clears API/runtime context around every test |
| `DriverCleanupListener` | Quits WebDriver/AppiumDriver after every test |
| `ExtentReportingListener` | Creates/finalizes the Extent report node per scenario — skips API scenarios entirely (see Reporting) |
| `ScreenshotCaptureListener` | Captures + attaches a failure screenshot to both reports |
| `AllureParameterMaskingListener` | Masks every Allure "Parameters" entry (`@DataProvider` rows included) before `allure-testng` writes the result to disk — closes the `toString()`-bypasses-the-masker gap |
| `ApiTestReportListener` | Starts/finalizes an API scenario's record on the separate `reports/api/` report (see Reporting) |
| `BeforeMethodAlwaysRunListener` | Fails the suite at start-of-run if any framework-internal `@BeforeMethod` has no `groups()` and no `alwaysRun = true` — turns a silent-skip-under-tag-filtering footgun into a loud, actionable one |
| `AllureMetadataListener` | Feature/Story/Severity/Platform labels on Allure, derived from a scenario's real Gherkin tags/name (see Reporting) |

`CucumberScenarioSupport` (`com.framework.listeners`, not itself a listener) is what makes the
three listeners above see a scenario's real Gherkin tags/name instead of `cucumber-testng`'s one
shared `runScenario` method and its generic annotation — see its own javadoc.

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

# Mobile only, before -Dcucumber.filter.tags="@mobile": boot an emulator/simulator, then
appium --base-path /wd/hub
```

**Appium's `--base-path` must be `/wd/hub`**, matching `appium.server.url` in
`config/{env}.properties` — Appium 2.x/3.x serves at the bare `/` root by default (no legacy
prefix) unless told otherwise, and a mismatch here surfaces as `SessionNotCreatedException:
... Response code 404` on every mobile test, not an obviously-Appium-related error. Before
running the command above, check whether a server is already up rather than assuming it isn't:
`curl -s http://127.0.0.1:4723/wd/hub/status` — a `200` with `"ready":true` means one's already
running correctly and there's nothing to start. An `EADDRINUSE`/"address already in use" error
from the command itself means the same thing (port 4723 already bound); only kill the existing
process (`pkill -f 'appium --base-path'`) and restart if you actually need to (e.g. picked up a
global Appium config change) — otherwise leave it running and go straight to
`-Dcucumber.filter.tags="@mobile"`.

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
| Reporting | `report.overwrite` — `true` (default): every run replaces `reports/extent/index.html` and `reports/api/index.html`. `false`: every run gets its own `reports/extent/report-{timestamp}.html`/`reports/api/report-{timestamp}.html`, so local runs stay side by side. `report.types` — comma-separated subset of `extent`\|`allure` (default `extent`), which report(s) this framework's own code enriches for Web/Mobile — no effect on the API report, which is separate and always renders. `report.api.title`/`report.api.name` — optional branding for the API report only — see [Reporting](#reporting) |
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

Every scenario selection is a Cucumber tag expression or a runner/feature-file/scenario-name
selector - never a suite XML, matching this repo's existing "everything is a `-D` flag"
philosophy. `-Dgroups`/`-DexcludedGroups` (plain TestNG groups) no longer select anything
scenario-scoped: every scenario physically runs through one shared TestNG method
(`AbstractTestNGCucumberTests.runScenario`), so TestNG's own group filter can't see a scenario's
tags - see `com.framework.listeners.CucumberScenarioSupport`'s javadoc for how the reporting
listeners still recover them.

```bash
mvn clean compile

# Default "everything green" run — excludes mobile (no local emulator by default)
mvn test -Dcucumber.filter.tags="not @mobile"

mvn clean test -Denv=qa -Dcucumber.filter.tags="@smoke" -Dbrowser=chrome -Dheadless=true
mvn test -Dcucumber.filter.tags="@api"                                     # one surface (every API scenario)
mvn test -Dcucumber.filter.name="Logging in with an existing account works" # one scenario, by name
mvn test -Dcucumber.features=src/test/resources/features/api/auth.feature   # one feature file
mvn test -Dcucumber.filter.tags="@smoke and @api"                          # several tags
mvn test -Dcucumber.filter.tags="@sanity and not @mobile"                  # one live test per surface
mvn test -Dcucumber.filter.tags="@api" -Ddataproviderthreadcount=4   # parallel, one scenario per thread
```

Real tags every scenario carries, four independent axes combinable in any tag expression:

| Axis | Values | Meaning |
|---|---|---|
| Run tier | `@smoke`, `@sanity` | `@smoke` = broad, every PR; `@sanity` = one narrowest "is anything even up" check per surface — see [CI/CD](#cicd) |
| Surface | `@api`, `@web`, `@mobile` | which stack drives the scenario |
| Test shape | `@positive`, `@negative`, `@e2e` | `@positive`/`@negative` = single-endpoint/screen happy-path vs. rejection case; `@e2e` = a multi-step cross-resource journey (never combined with positive/negative - it's its own shape) |
| Resource | `@auth`, `@events`, `@bookings`, `@system` | which domain the scenario covers - an `@e2e` scenario carries every resource its journey touches (e.g. both `@events` and `@bookings`) |

`-Dcucumber.filter.tags` is a proper Cucumber tag expression, not a comma list —
`and`/`or`/`not`, parenthesized as needed:

```bash
mvn test -Dcucumber.filter.tags="@negative"                          # every rejection/validation scenario, any surface
mvn test -Dcucumber.filter.tags="@events and not @mobile"            # AND / NOT - every events-domain scenario, Web+API
mvn test -Dcucumber.filter.tags="@e2e"                                # every multi-step journey, any surface
mvn test -Dcucumber.filter.tags="@bookings and @negative"             # booking rejection scenarios specifically
mvn test -Dcucumber.filter.tags="@web or @api"                        # OR - either surface, no mobile
mvn test -Dcucumber.filter.tags="(@events or @bookings) and @negative" # parenthesized - negative cases in either domain
```

A tag expression matching nothing still reports `BUILD SUCCESS` (0 scenarios run) —
check the printed test count.

**Every run is parallel by default — up to 10 scenarios at once, not 1** (see [Parallel
execution](#parallel-execution) below for the full explanation and why `-Dparallel`/
`-DthreadCount` have no effect here; `-Ddataproviderthreadcount=N` is the actual control knob,
`=1` for strictly sequential).

**Rerunning only what failed:** Surefire's TestNG provider writes
`target/surefire-reports/testng-failed.xml` after every run — a suite file listing just the
scenarios that failed, regardless of the fact that this repo has no suite XML of its own.
Feed it straight back in to rerun only those:

```bash
mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test
```

Confirmed live: after a run with one failing scenario, this reran exactly that scenario and
nothing else. Overwritten by the next full run, so grab a copy first if you want to keep
retrying a specific failure while iterating on other tests.

**API** — every feature in `features/api/`: `auth.feature`, `events.feature`,
`bookings.feature`, `system.feature`, `booking_e2e_flow.feature`, selected with `@api`.

```bash
mvn test -Dcucumber.filter.tags="@api"                                              # every API scenario
mvn test -Dcucumber.filter.tags="@smoke and @api"                                   # just the smoke-tagged ones
mvn test -Dcucumber.features=src/test/resources/features/api/auth.feature          # one feature file
mvn test -Dcucumber.filter.name="Logging in with an existing account works"        # one scenario, by name
mvn test -Dcucumber.features="src/test/resources/features/api/auth.feature,src/test/resources/features/api/booking_e2e_flow.feature"  # several feature files
mvn test -Dcucumber.filter.tags="@api" -Ddataproviderthreadcount=4           # parallel, one scenario per thread
mvn test -Dcucumber.filter.tags="@api" -Ddataproviderthreadcount=8           # same, more concurrent load on the API
mvn test -Denv=dev -Dcucumber.filter.tags="@api"                                    # against dev instead of qa
```

**Web** — `features/web/login.feature`, `events.feature`, selected with `@web`.

```bash
mvn test -Dcucumber.filter.tags="@web"                                              # every Web scenario
mvn test -Dcucumber.filter.tags="@smoke and @web"                                   # just the smoke-tagged ones
mvn test -Dcucumber.features=src/test/resources/features/web/login.feature          # one feature file
mvn test -Dcucumber.filter.name="Valid login navigates to the home page"            # one scenario, by name
mvn test -Dcucumber.filter.tags="@web" -Dbrowser=firefox -Dheadless=true             # browser: chrome (default) | firefox | edge | safari
                                                                                      # (cross-browser coverage is CI's job matrix, not a per-scenario loop - see CI/CD)
mvn test -Dcucumber.filter.tags="@web" -Ddataproviderthreadcount=4 -Dheadless=true
mvn test -Denv=dev -Dcucumber.filter.tags="@web" -Dbrowser=chrome -Dheadless=true    # against dev instead of qa
```

**Mobile** — `features/mobile/login.feature`, `events.feature`, `booking_e2e_flow.feature`,
selected with `@mobile`. eventhub's own Flutter app (replaces an earlier suite against the
public Sauce Labs SwagLabs demo app, removed) - login, browse/search events, and a full
login→book→confirm→My Bookings journey, each verified live against a real iPhone 17 Pro/
iPhone 17 Simulator session before being committed (Appium + XCUITest, real page source, not
guessed locators). Device details are never passed on the CLI; whether a run is sequential (one
device) or parallel (every configured device, pooled) depends only on whether `-Dparallel` is
present:

```bash
mvn test -Dcucumber.filter.tags="@mobile"                                                    # sequential, one device (android by default)
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.platform=ios                               # sequential, iOS instead - one line, no other flags
mvn test -Dcucumber.features=src/test/resources/features/mobile/login.feature                # one feature file
mvn test -Dcucumber.filter.name="Valid credentials log in and show the home screen"           # one scenario, by name
mvn test -Dcucumber.features="src/test/resources/features/mobile/login.feature,src/test/resources/features/mobile/events.feature,src/test/resources/features/mobile/booking_e2e_flow.feature"  # several feature files
mvn test -Dcucumber.filter.tags="@mobile" -Ddataproviderthreadcount=3                   # pooled across every configured device
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.device.provider=BROWSERSTACK ...           # real device / cloud farm, see BrowserStack
mvn test -Denv=dev -Dcucumber.filter.tags="@mobile"                                            # against dev instead of qa
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
`login.feature`'s negative scenarios are the client-side form validation Flutter itself enforces
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
  "ports": { "systemPort": { "start": 8200, "count": 50 } }
}
```

- **Every scenario always draws from `MobileDevicePool`** (`androidList` + `iosList` combined by
  default) — there's no separate "sequential mode"; a run with only one device configured (or
  narrowed to one via `-Dmobile.platform`) is just a pool of one, so concurrent scenario threads
  queue for it one at a time, the same practical effect a dedicated sequential code path used to
  give, without needing one. `mvn test -Dcucumber.filter.tags="@mobile"` alone is therefore
  effectively sequential today (`androidList` has one entry) purely because of what's in
  `config/mobile-devices.json`, not because of any command-line flag.
- **`-Dmobile.platform=ios`** (or `android`) narrows the pool to just that platform's list —
  e.g. `-Dmobile.platform=ios -Ddataproviderthreadcount=2` pools across `iosList` only (both
  configured simulators, genuinely concurrent), useful when no Android emulator/device happens
  to be available for a given run. Omitting it keeps the combined-pool default. (An explicit
  `-Dmobile.device.name=...` still overrides everything for a genuine one-off, but it's never
  required.)
- **Which app binary** each platform installs is an environment/build concern, so it's in
  `config/{env}.properties`, not the JSON file: `mobile.app.path.android`,
  `mobile.app.path.ios` — one entry per platform, since every device on that platform runs the
  same build.

Add a device, or point `androidList`/`iosList`/`mobile.platform` at a different one, by editing
`config/mobile-devices.json` (devices/lists) or `config/{env}.properties` (`mobile.platform`) —
no code to touch. See `com.framework.driver.MobileDeviceMatrix`/`MobileDevicePool`.

**Appium ports:** `appium.server.url` (`http://127.0.0.1:4723/wd/hub` by default) is the
*one* Appium server every `LOCAL` session — sequential or pooled — connects through; one
HTTP port legitimately serving many concurrent sessions is normal, the same as any web
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

**Web** (`features/web/login.feature` + `steps/web/LoginSteps`):

```gherkin
@smoke @web
Scenario: Valid login navigates to the home page
  Given I am on the login page
  When I log in with the "validLogin" web test data
  Then the home page should be displayed
```

```java
@Given("I am on the login page")
public void iAmOnTheLoginPage() {
    context.loginPage = new LoginPage().open(ConfigManager.getBaseUrl());
}

@When("I log in with the {string} web test data")
public void iLogInWithTheWebTestData(String caseName) {
    LoginData data = TestDataSurface.WEB.getCaseData(caseName, LoginTestCase.class);
    context.loginPage.enterEmail(data.email()).enterPassword(data.password()).clickLogin();
}

@Then("the home page should be displayed")
public void theHomePageShouldBeDisplayed() {
    context.homePage = new HomePage();
    assertTrue(context.homePage.isDisplayed(), "...");
}
```

Page Objects extend `BasePage`, expose business-level actions only, and log each step
(`logger.info(...)`), which mirrors automatically into the Extent report. `assertTrue` here is
`com.framework.utils.Verify`'s, not `org.testng.Assert`'s directly — same signature (drop-in,
just a different static import), but logs its own PASS/FAIL step to both reports as it runs
instead of being invisible until the scenario's final summary. `context` is a
`WebScenarioContext` (`steps/shared/`), constructor-injected via `cucumber-picocontainer` so
`LoginSteps` and every other Web step-definition class in the same scenario share the same page
objects. See [Reporting](#reporting) and [Writing tests](#writing-tests).

**Mobile** (`features/mobile/login.feature` + `steps/mobile/LoginSteps`):

```gherkin
@smoke @sanity @mobile
Scenario: Valid credentials log in and show the home screen
  Given the app is launched logged out
  When I log in with the "validCredentials" mobile test data
  Then the home screen should be displayed
```

```java
@When("I log in with the {string} mobile test data")
public void iLogInWithTheMobileTestData(String caseName) {
    LoginData data = TestDataSurface.currentMobile().getCaseData(caseName, LoginTestCase.class);
    context.loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();
}
```

Mirrors Web exactly (`BaseMobilePage`, W3C `PointerInput` gestures, not the deprecated
`TouchAction`). Locators are the Flutter app's own Semantics labels - the same value Appium
exposes as `accessibilityId` on iOS (`content-desc` on Android, since one Flutter Semantics
tree drives both) - rather than dedicated `test-*` ids (the earlier SwagLabs app's own
convention, not something this codebase controls).

**API** (`features/api/auth.feature` + `steps/api/AuthSteps`):

```gherkin
@smoke @api @auth
Scenario: Logging in with an existing account works
  When I log in with the "loginExistingAccount" auth test data
  Then the login should report success with a usable token matching the account logged in with
```

```java
@When("I log in with the {string} auth test data")
public void iLogInWithTheAuthTestData(String caseName) {
    AuthApiData caseData = data(caseName);
    authResponse = context.authService.login(caseData.email(), caseData.password());
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
mvn test -Dcucumber.filter.tags="@api" -Ddataproviderthreadcount=4
```

**Every run is parallel by default, up to 10 scenarios at once — `-Dparallel`/`-DthreadCount`
have no effect on this at all.** Every scenario is one row of the single `RunCucumberTest`
runner's own `@DataProvider(parallel = true)` (`cucumber-testng`'s documented mechanism, not a
custom addition). TestNG spreads a *parallel data provider's* invocations across its own
dedicated thread pool — sized by `-Ddataproviderthreadcount` (default `10`) — completely
independently of whether `-Dparallel=methods`/`-Dparallel=classes` and `-DthreadCount` are
passed at all; those two flags parallelize separate `@Test` *methods*/classes against each
other, and this project has exactly one (`runScenario`), so they're inert here. Audit finding,
verified by disassembling the pinned `cucumber-testng` jar (`scenarios()` carries a bare
`@DataProvider` with no `parallel` attribute at all - defaulting to `false` - unless overridden,
which `RunCucumberTest` now does): an earlier version of this project documented
`-Dparallel=methods -DthreadCount=N` as the parallelism control here, which never actually took
effect, so every "verified live with N threads" claim from that period was unknowingly running
single-threaded the whole time.

```bash
mvn test -Ddataproviderthreadcount=1     # strictly sequential, one scenario at a time
mvn test -Ddataproviderthreadcount=20    # a wider pool, e.g. on a beefier CI runner
```

Mobile parallelizes through the same mechanism, distributing scenarios across every configured
device as a work queue instead of one shared device — see [Running tests](#running-tests) and
`MobileDevicePool`'s own javadoc. Distinct `systemPort`/`wdaLocalPort`/`chromedriverPort` per
session are handled automatically, and the pool always draws from every configured device
regardless of pool size (a pool of one device is just as correct, only sequential in practice) —
genuine parallel mobile still needs multiple emulators/devices/BrowserStack capacity actually
booted, since one local emulator only runs one session at a time no matter how wide the pool is.

## Reporting

**API scenarios use a completely separate report from Web/Mobile** — `reports/api/index.html`, a
self-contained, dependency-free HTML dashboard (no Extent, no Allure, no CDN/network access
assumed) grouped by module (Authentication/Events/Bookings/Health & Config/End-to-End), one
collapsible row per scenario with full request/response detail and Expected/Actual assertions -
see [API Report](#api-report) below. Everything in this section past that is Web/Mobile only:
`com.framework.api`/`com.framework.utils.Verify` never touch Extent or Allure for an API
scenario (`ApiTestReportListener`/`ExtentReportingListener`/`AllureMetadataListener` all key off
the `@api` Gherkin tag, via `CucumberScenarioSupport`, to keep the two fully separate), and
`report.types` below has no effect on the API report at all - it always renders, controlled only
by `report.overwrite`.

**Which report(s) get this framework's own enrichment is configurable** —
`report.types` (default `extent`; `allure`, or `extent,allure` — see
[Configuration](#configuration), Web/Mobile only - see the note above for API). Extent, after
the work below, is the fully-enriched, actively-used report; Allure is comparatively thin (no
bridge for business-narrative log lines, no assertion detail - see its own bullet below) and its
one real differentiator, cross-run trend/history dashboards, isn't wired up in this project's CI
today.
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
  in the business-narrative layer (Web/Mobile Page Objects and Components) mirrors into the
  report automatically via a Logback appender. The test title itself is the scenario's own
  Gherkin name (`Invalid login shows an error and stays on the login page`, not a Java method
  name at all - `AbstractTestNGCucumberTests.runScenario` is the only physical method every
  scenario invokes) — see `ExtentReportingListener`/`CucumberScenarioSupport`.
- **Assertion detail in Extent/Allure** — every assertion logs its own PASS/FAIL step inline,
  where it happened, not just the scenario's final summary: `com.framework.utils.Verify` (a
  drop-in `org.testng.Assert.assertTrue`/`assertFalse`/`assertEquals`/`assertNotNull`
  replacement — same signatures, swap the static import) for Web/Mobile step-definition
  assertions. Neither changes assertion semantics — it still delegates to (or throws exactly
  like) the real thing; it only adds the missing report step around it. For an API scenario,
  `Verify` and `ApiResponse#assertStatusCode` report to the separate [API Report](#api-report)
  instead — see that section, not this one.
- **Allure** (`allure-results/`, raw JSON — `allure serve allure-results` to view), when
  `report.types` includes `allure`. With `allure` excluded, `allure-results/` still gets
  `allure-testng`'s own bare pass/fail entries (see above), just none of this:
  - **Steps** — the same business-narrative log lines Extent's bridge mirrors (Web/Mobile Page
    Objects and Components) also reach Allure as steps (`AllureLoggingAppender`, wired to the
    same loggers as `ExtentLoggingAppender`) - closes what used to be a documented gap ("Allure
    has no bridge for arbitrary log lines"). API tests have no equivalent here - see the note at
    the top of this section.
  - **Web** — screenshot, current URL, browser + version, and masked page source, all attached
    on failure (`ScreenshotCaptureListener`, same correctly-ordered "driver still alive" window
    the screenshot itself already relied on).
  - **Mobile** — screenshot, device name, platform + version, current activity (Android only -
    no iOS equivalent, best-effort), and masked page source, all attached on failure (same
    listener/window as Web).
  - **Feature/Story/Severity/Platform labels** (Web/Mobile only - `AllureMetadataListener`
    skips API scenarios the same way `ExtentReportingListener` does, see the note at the top of
    this section) and an **Environment** widget (`allure-results/environment.properties`, every
    run regardless of surface) - all derived automatically from data this framework already has
    (a scenario's own Gherkin tags, via `CucumberScenarioSupport`; `ConfigManager`). Retry
    grouping and parallel/timeline data are native to `allure-testng` already; nothing extra was
    needed for either.
- **Test case metadata** — every `TestDataManager.getCaseData(...)` call (i.e. every test data
  load) logs its `testCaseId`/`testCaseName` (reaching Extent for free via the Logback bridge
  above) and attaches them as Allure parameters (`AllureManager.attachTestCaseMetadata`) - so a
  failure in either report is immediately traceable to the exact named test case (and its
  file/row), filterable/sortable in Allure, without a step-definition author adding any
  reporting code.
- **Screenshots** — `screenshot.mode` = `FAILURE` (default) | `EVERY_ACTION` | `DISABLED`.
- **Retry** — `RetryAnalyzer` (`retry.max.count`, default 1) retries everything except
  `AssertionError`; a retried attempt's own report entry is kept, labeled `(Retry N)`. The same
  policy/limit also covers `@BeforeMethod`/`@AfterMethod` (`ConfigurationRetryListener`) - a
  transient failure in per-scenario setup/teardown (e.g. an API scenario's login hitting a
  momentary 502) gets the same retry a scenario's own steps would, instead of failing outright.
- **Coverage** — `mvn test` also runs Jacoco (`jacoco-maven-plugin`, bound to the `test` phase
  itself, not `verify`, so plain `mvn test` is enough): `target/site/jacoco/index.html` for a
  human-readable `com.framework.*` line-coverage report. Not gated - the `com.tests.base`
  framework self-test suite this used to be checked against was removed, dropping measured
  coverage well below any threshold worth enforcing without a real suite behind it; the report
  is still generated for visibility. Re-add a `jacoco-check` execution with a calibrated minimum
  if framework-level self-tests come back.

### API Report

`reports/api/index.html` (or `reports/api/report-{timestamp}.html` if `report.overwrite=false`)
- a self-contained, dependency-free HTML dashboard for the API surface only, matching the
reporting structure of the standalone `RestAssuredTestNG` framework this was ported from.
No Extent, no Allure, no third-party reporting library at all - everything (CSS/JS) is inlined,
so the file opens standalone from disk or a CI artifact download with no network access
assumed.

- **Summary cards** — total/passed/failed/skipped test counts and total suite duration, plus a
  perf strip (average response time, total API calls, slowest call).
- **Grouped by module** — `Authentication`/`Bookings`/`Events`/`Health & Config`/`End-to-End`,
  derived from the same Gherkin tags every API scenario already carries (`@auth`/`@bookings`/
  `@events`/`@system`/`@e2e`) - see `ApiTestReportListener`.
- **One collapsible row per scenario** — pass/fail/skip icon, the scenario's own Gherkin name
  (`CucumberScenarioSupport#displayName`), and either the single call's method/endpoint/status/
  duration inline, or a call count for a multi-call scenario (e.g. `booking_e2e_flow.feature`'s
  create-event/book/verify/cleanup sequence) - expand for full detail: masked request headers/
  body and response body per call (each in its own collapsible, copyable code block), and every
  `Verify`/`ApiResponse#assertStatusCode` check made against it as a plain Expected/Actual pair,
  not a technical assertion sentence.
- **Search + tag filters** — filter by scenario name/endpoint text, or toggle any Gherkin tag
  (`smoke`, `positive`, `negative`, ...) to narrow the visible rows - both client-side, no
  server/build step involved.
- **Status pills are display-only** — colored purely by HTTP status class (2xx green, 4xx amber,
  5xx red) for at-a-glance reading; they are *not* the test's pass/fail signal. A test that
  deliberately asserts a 400/404 is exactly as green as one asserting 200 - only the Validations
  section and the row's own icon decide red vs. green.

Branding (`report.api.title`/`report.api.name`, both optional - see `ConfigKeys`) and the
overwrite behavior (`report.overwrite`, shared with Extent's) are the only configurable knobs;
there is no equivalent of `report.types` here - the API report always renders when at least one
API scenario ran this suite, and is skipped entirely (not written as an empty file) on a pure
Web/Mobile run.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) is the only CI config in this repo, running the
same `mvn` command shape you'd run locally:

| Job | Trigger | Command shape |
|---|---|---|
| `smoke` | Every pull request | Matrix over `chrome`/`firefox`: `-Denv=qa -Dcucumber.filter.tags="@smoke and not @mobile" -Dbrowser=<matrix> -Dheadless=true` |
| `regression` | Push to `main` | Same matrix, `-Dcucumber.filter.tags="not @mobile"` (every tag but mobile) |
| `regression-manual` | Manual dispatch | Single browser — `env`/`tags`/`browser` from the dispatch inputs (default `qa`/every tag/`chrome`), always ANDed with `not @mobile` |

`smoke`/`regression` run a real `chrome`+`firefox` matrix (`fail-fast: false`, so one browser's
failure doesn't cancel the other) — `ubuntu-latest` only guarantees Chrome and Firefox
preinstalled, not Edge/Safari, so those stay local/self-hosted-only for now. A manual dispatch
stays single-browser on purpose: it's normally someone deliberately targeting one specific
combination, not asking for the full sweep.

Every job archives `target/surefire-reports/`, `logs/`, `reports/extent/`, `reports/api/`,
`allure-results/`, `target/screenshots/`, and `target/site/jacoco/` regardless of pass/fail
(per-browser artifact names, since a matrix run can't share one name across its own legs).
`reports/api/` only has content when the job's tag filter actually included any `@api`
scenarios - the smoke/regression jobs above (`not @mobile`, no `@api` exclusion) do include
them. None of these jobs pass
`-Dreport.types=...`, so Web/Mobile get the default (`extent` only, see [Reporting](#reporting)) -
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
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.device.provider=BROWSERSTACK \
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

Every test is a Gherkin scenario. Step definitions call only business-level Page Object/Service
methods — never raw Selenium/Appium/REST Assured. Retry, logging, reporting, and driver cleanup
are automatic.

> **A framework-internal `@BeforeMethod` (not a Cucumber `@Before` hook) still needs
> `alwaysRun = true`.** `BeforeMethodAlwaysRunListener` fails the suite at start-of-run with the
> exact `Class#method` if one is missing it, rather than letting it silently stop running the
> moment a tag filter narrows a run - still worth getting right the first time, but no longer a
> silent trap if you don't. Cucumber's own `@Before`/`@After` hooks (`com.tests.hooks.*`) always
> run for every scenario matching their tag expression regardless of `-Dcucumber.filter.tags`,
> so this doesn't apply to them.

**New scenario, existing feature:** add a `Scenario:`/`Scenario Outline:` to the relevant
`.feature` file with whatever tags apply (surface/tier/shape/resource - see [Running
tests](#running-tests)), then add or reuse `Given`/`When`/`Then` steps in that surface's
`com.tests.steps.*` package. A step whose text already exists anywhere is reused automatically
(the single `RunCucumberTest` runner loads every surface's glue together, and Cucumber matches
by text across every step-definition class, not per-class or per-surface) - only add a new
method when no existing step already says what you need, and give it text distinct from
*every* other step-definition class project-wide if it reads/writes that class's own private
fields (see `steps/api/BookingE2EFlowSteps`'s javadoc for why that matters).

**New feature file:** create it under the right `features/<surface>/` directory; it's picked up
automatically (the single `RunCucumberTest` runner already points at `features/**`) — no
runner/glue config change needed unless the new feature introduces a step-definition package
that doesn't exist yet.

**Scenario state, not a base class:** there's no inheritance hierarchy to extend any more -
each surface has one `*ScenarioContext` class (`steps/shared/`) holding page objects/services
and whatever a scenario needs cleaned up afterward, constructor-injected via
`cucumber-picocontainer` into every step-definition/hook class in that scenario. A step that
needs a specific *starting state* (e.g. logged in, or deliberately logged out) is itself a
`Given` step or a `Background:` — see `steps/web/EventsSteps`/`features/web/events.feature` for
the pattern. Teardown of anything a scenario creates (a booking, an event) goes on the shared
context (e.g. `context.createdEventId = id`) for that surface's `*Hooks` class
(`com.tests.hooks.*`, a Cucumber `@After("@tag")` hook) to release automatically - see
`ApiHooks`/`MobileHooks`.

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

**Test class instance fields used to be the one place this bit easily, and did - before the
BDD migration.** TestNG ran every `@Test` method of a class on one shared instance under
`parallel="methods"` — not one instance per thread/method — so a plain instance field a test
wrote in its own body and read back later (typically to hand to `tearDownTestData()`) was
exactly as unsafe as any other shared mutable state without `ThreadLocal`. Audit finding,
verified live: `mvn test -Dgroups=api -Dparallel=methods -DthreadCount=8` (against the old
plain-TestNG `EventApiTest`/`EventBookingE2EFlowTest`) reliably reproduced two failures where
one thread's `createdEventId`/`createdBookingId` write was clobbered by a different `@Test`
method running concurrently on the same class instance, before the first thread read it back -
worse, `tearDownTestData()` read the same field after the method returned, so a lost write
could make it cancel/delete a *different* thread's still-in-use booking/event instead of its
own. The fix at the time was `ThreadLocal<Integer>` fields, same convention as
`ApiContext`/`ConfigManager` above.

**Cucumber's own execution model closed this class of bug outright, not just worked around
it.** `cucumber-picocontainer` creates one fresh instance of every step-definition class (and
its `ApiScenarioContext`) per scenario - never shared across threads or reused across
scenarios - so `steps/api/BookingE2EFlowSteps`' `createdBookingId`/`createdEventId` are plain
`int` fields today, not `ThreadLocal`, and are correct under `-Dparallel=methods` by
construction. This is a genuine simplification the migration surfaced, not merely a port of the
old workaround.

Validated live: `-Dcucumber.filter.tags="@api" -Ddataproviderthreadcount=6` with genuinely
concurrent API calls across 10 real `TestNG-PoolService-N` threads (confirmed by grepping thread
names straight out of `logs/framework.log`, not assumed from `-D` flags alone - see [Parallel
execution](#parallel-execution) for why `-DthreadCount` itself turned out not to matter); and
two real, simultaneously-booted iOS simulators both allocated distinct `wdaLocalPort`s and used
concurrently via `-Dcucumber.filter.tags="@mobile" -Dmobile.platform=ios
-Ddataproviderthreadcount=2` pooling across `MobileDevicePool`, confirmed via the device name
alternating across `Allocated wdaLocalPort ... for device '...'` log lines from real, distinct
threads.

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Mobile fails with `SessionNotCreated` | Two distinct causes, same exception type. **No emulator/Appium server running at all** — start both, or exclude mobile (`-Dcucumber.filter.tags="not @mobile"`). **Server running but wrong base path** (message says `Response code 404`, not a connection failure) — Appium is up but not serving `/wd/hub` (its 2.x/3.x default is the bare `/` root); confirm with `curl http://127.0.0.1:4723/wd/hub/status` (should be `200`) and restart with `appium --base-path /wd/hub` if it isn't — see [Setup](#setup). |
| `-Dcucumber.filter.tags="@x"` runs zero scenarios but still `BUILD SUCCESS` | `@x` isn't a real tag — see the [tag taxonomy table](#running-tests) (`@smoke`/`@sanity`, `@api`/`@web`/`@mobile`, `@positive`/`@negative`/`@e2e`, `@auth`/`@events`/`@bookings`/`@system`). Check the printed test count. |
| A framework-internal `@BeforeMethod` missing `alwaysRun = true` under a tag filter | Can't happen silently any more — `BeforeMethodAlwaysRunListener` fails the suite at start-of-run with the exact `Class#method` if this is ever missing (see Listeners). Cucumber's own `@Before`/`@After` hooks aren't affected by this at all - they run per their own tag expression regardless of `-Dcucumber.filter.tags`. |
| A report shows secret values in plain text | Expected by default — masking is off unless enabled (see Secrets). Turn it on for a run you intend to share: `mvn test -Dmasking.enabled=true ...` (or `MASKING_ENABLED=true`). CI always masks regardless of this flag. |
| Masking looks missing on a new log/report line despite `-Dmasking.enabled=true` | Shouldn't happen when masking is on — CONSOLE/FILE/Extent/Allure parameters, and the API report, all mask every line/value unconditionally once enabled (see Reporting). If it does, that line went through some other sink entirely (e.g. a raw `System.out.println`, or a custom appender bypassing `logback.xml`'s pattern), not a missed `.mask()` call. |
| Two masked values, want to know if they're the same secret | Compare the `********-xxxxxxxx` suffix — same secret always produces the same fingerprint. |
| Web browser config looks wrong under `parallel="classes"` | `ConfigParameterListener` resets config before every invoked method — if this recurs, check nothing else caches a config value. |
| `Log4j2 could not find a logging implementation` | Harmless — Apache POI's internal logger falling back to SimpleLogger. |
| Selenium CDP version warnings | Harmless — Chrome's DevTools Protocol is newer than Selenium's bundled client. |
| Want to rerun just what failed, not the whole run | `mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test` — see [Running tests](#running-tests). |

**Where to look:** `logs/framework.log` (MDC-tagged per thread), `reports/extent/index.html`
(Web/Mobile), `reports/api/index.html` (API - see [API Report](#api-report)), `allure-results/`
(`allure serve allure-results`), `target/screenshots/`, `target/surefire-reports/`.

**Debug from an IDE:** most IDEs with Cucumber/Gherkin support let you right-click a `Scenario:`
in a `.feature` file and Debug it directly (breakpoints in the matching step-definition method
work as normal); otherwise right-click the single `RunCucumberTest` runner class to debug
everything. From the CLI: `mvn test -Dmaven.surefire.debug`, then attach on `localhost:5005`.
