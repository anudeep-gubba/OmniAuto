# Running the tests — API, Web, Mobile (Android & iOS)

Command reference for running this suite. Every test is a Gherkin scenario (Cucumber, driven
through TestNG via `cucumber-testng` — see `README.md`'s "BDD / Cucumber" section for the
architecture). There is exactly one runner class, `com.tests.runners.RunCucumberTest`, and
Surefire's own classpath-wide TestNG discovery picks it up automatically on a bare `mvn test` —
**everything below is a tag expression, no `-Dtest=` needed at all.** No suite XML in this repo
either, so picking a subset is never a file edit. See `README.md` for the full framework
documentation; this file is just the "what do I actually type" cheat sheet.

## Contents

1. [One-time setup](#one-time-setup)
2. [API testing](#api-testing)
3. [Web testing](#web-testing)
4. [Mobile testing — Android](#mobile-testing--android)
5. [Mobile testing — iOS](#mobile-testing--ios)
6. [Mobile — parallel execution (pooled devices)](#mobile--parallel-execution-pooled-devices)
7. [Mobile — BrowserStack (real devices / cloud)](#mobile--browserstack-real-devices--cloud)
8. [Useful extras](#useful-extras)
9. [Troubleshooting](#troubleshooting)

---

## One-time setup

Required everywhere: **JDK 17+**, **Maven 3.9+**, **Git**.

```bash
git clone <repo-url> && cd OmniAuto
cp .secret.env.example .secret.env   # fill in real values - EVENTHUB_EMAIL / EVENTHUB_PASSWORD etc.
mvn clean compile
```

`.secret.env` is git-ignored — never commit it. It backs the `${{EVENTHUB_EMAIL}}` /
`${{EVENTHUB_PASSWORD}}` placeholders used throughout Web/API/Mobile test data.

**Web only:** Chrome and/or Firefox — Selenium Manager resolves the matching driver
automatically, nothing to install by hand. Safari needs `safaridriver --enable` plus
Safari > Develop > Allow Remote Automation (no headless mode for Safari).

**Mobile only:** Appium 3.x with the driver(s) for whichever platform you're testing:

```bash
npm i -g appium
appium driver install uiautomator2   # Android
appium driver install xcuitest       # iOS (macOS only)
```

Every command below assumes `qa` as the environment (the default when `-Denv` is omitted — add
`-Denv=dev` to any command to target `dev` instead). Every scenario carries Gherkin `@tags`
along four independent axes, combinable in any `-Dcucumber.filter.tags` expression (full table
in `README.md`'s "Running tests" section):

- **Run tier:** `@smoke`, `@sanity`
- **Surface:** `@api`, `@web`, `@mobile`
- **Test shape:** `@positive`, `@negative`, `@e2e`
- **Resource:** `@auth`, `@events`, `@bookings`, `@system`

`-Dcucumber.filter.tags` takes a proper Cucumber tag expression, not a comma list —
`and`/`or`/`not`, parenthesized as needed:

```bash
mvn test -Dcucumber.filter.tags="@negative"                          # every rejection/validation scenario, any surface
mvn test -Dcucumber.filter.tags="@events and not @mobile"            # AND / NOT - every events-domain scenario, Web+API
mvn test -Dcucumber.filter.tags="@e2e"                                # every multi-step journey, any surface
mvn test -Dcucumber.filter.tags="@bookings and @negative"             # booking rejection scenarios specifically
mvn test -Dcucumber.filter.tags="@web or @api"                        # OR - either surface, no mobile
mvn test -Dcucumber.filter.tags="(@events or @bookings) and @negative" # parenthesized - negative cases in either domain
```

A tag expression matching nothing still reports `BUILD SUCCESS` (0 scenarios run) — always
check the printed test count.

**Every run is parallel by default — up to 10 scenarios at once, not 1.** Every scenario is a
row of the single `RunCucumberTest` runner's own `@DataProvider(parallel = true)`
(`cucumber-testng`'s documented mechanism), so TestNG dispatches them across its own thread
pool regardless of whether `-Dparallel`/`-DthreadCount` are passed at all — those two flags
control a *different* thing (parallelizing separate `@Test` *methods*/classes; there's only one
method, `runScenario`, in this whole suite) and have no effect here.
**`-Ddataproviderthreadcount=N`** is the actual control knob: narrow the pool
(`-Ddataproviderthreadcount=2`), or pass `-Ddataproviderthreadcount=1` for strictly sequential,
single-threaded execution (e.g. while debugging one scenario at a time). Mobile is safe at any
pool size regardless — `MobileDevicePool` blocks/queues any thread beyond the number of
configured devices rather than oversubscribing one — but Web opens one concurrent browser
window per in-flight scenario, up to the pool size, so narrow it on a resource-constrained
machine.

---

## API testing

No browser, no emulator — just the live API (`api.base.url` in `config/{env}.properties`).
Real features: `features/api/auth.feature`, `events.feature`, `bookings.feature`,
`system.feature`, `booking_e2e_flow.feature` — step definitions in `com.tests.steps.api.*`.

```bash
# Every API scenario
mvn test -Dcucumber.filter.tags="@api"

# Just the smoke-tagged API scenarios (fast subset)
mvn test -Dcucumber.filter.tags="@smoke and @api"

# One feature file
mvn test -Dcucumber.features=src/test/resources/features/api/auth.feature

# One scenario, by (a regex matching) its Gherkin name
mvn test -Dcucumber.filter.name="Logging in with an existing account works"

# Several feature files at once
mvn test -Dcucumber.features="src/test/resources/features/api/auth.feature,src/test/resources/features/api/booking_e2e_flow.feature"

# Parallel - one scenario per thread
mvn test -Dcucumber.filter.tags="@api" -Ddataproviderthreadcount=8

# Against a different environment
mvn test -Denv=dev -Dcucumber.filter.tags="@api"
```

**The one API sanity check** (fastest possible "is the API even up" signal, no auth/test data
needed — `system.feature`'s `/health` scenario):

```bash
mvn test -Dcucumber.filter.tags="@sanity and not @mobile"
```

---

## Web testing

Real features: `features/web/login.feature`, `events.feature` (against
eventhub.rahulshettyacademy.com) — step definitions in `com.tests.steps.web.*`.

```bash
# Every Web scenario
mvn test -Dcucumber.filter.tags="@web"

# Just the smoke-tagged Web scenarios
mvn test -Dcucumber.filter.tags="@smoke and @web"

# One feature file
mvn test -Dcucumber.features=src/test/resources/features/web/login.feature

# One scenario, by (a regex matching) its Gherkin name
mvn test -Dcucumber.filter.name="Valid login navigates to the home page"

# Choose a browser explicitly (default: chrome)
mvn test -Dcucumber.filter.tags="@web" -Dbrowser=firefox -Dheadless=true
# browser: chrome | firefox | edge | safari (safari can't run headless)

# Parallel - one scenario per thread
mvn test -Dcucumber.filter.tags="@web" -Ddataproviderthreadcount=4 -Dheadless=true

# Against a different environment
mvn test -Denv=dev -Dcucumber.filter.tags="@web" -Dbrowser=chrome -Dheadless=true
```

**Cross-browser coverage is not a per-scenario loop** — it's CI's own job matrix (`smoke`/
`regression` in `.github/workflows/ci.yml` run the *entire* Web suite once per browser, as
separate parallel jobs). Running the same command twice locally with two different `-Dbrowser`
values is how you reproduce that locally if needed; there's no single command that runs both at
once on purpose.

---

## Mobile testing — Android

Real features: `features/mobile/login.feature`, `events.feature`, `booking_e2e_flow.feature` —
step definitions in `com.tests.steps.mobile.*`. App under test:
`apps/eventhub-app-release.apk` (bundles an `x86_64` slice alongside device ABIs, so this one
file installs on both a real device and an emulator).

**Before any mobile command:**

1. Boot an Android emulator (AVD) — e.g. `Pixel_10` (the device this suite is tuned against;
   see `config/mobile-devices.json`).
2. Start Appium: `appium --base-path /wd/hub` — check first whether one's already running
   (`curl -s http://127.0.0.1:4723/wd/hub/status`; a `200` with `"ready":true` means it's
   already up, nothing to start). An `EADDRINUSE`/"address already in use" error from the
   command above means exactly that — port 4723 is already bound to a running Appium server,
   not a broken one. Only kill it (`pkill -f 'appium --base-path'`) if you actually need to
   restart it (e.g. after changing global Appium config); otherwise just leave it running and
   proceed straight to the test command.

```bash
# Sequential, Android (the default platform - no extra flag needed)
mvn test -Dcucumber.filter.tags="@mobile"

# One feature file
mvn test -Dcucumber.features=src/test/resources/features/mobile/login.feature

# One scenario, by (a regex matching) its Gherkin name
mvn test -Dcucumber.filter.name="Valid credentials log in and show the home screen"

# Several feature files
mvn test -Dcucumber.features="src/test/resources/features/mobile/login.feature,src/test/resources/features/mobile/events.feature,src/test/resources/features/mobile/booking_e2e_flow.feature"

# Against a different environment
mvn test -Denv=dev -Dcucumber.filter.tags="@mobile"
```

Android is the default because `mobile.platform=android` is what `config/qa.properties` /
`config/dev.properties` ship with — switch the *default* platform by editing that one property,
no `-D` flag or code change needed for a whole team to standardize on iOS instead. An explicit
`-Dmobile.platform=android` on the command line always works too, for a one-off.

---

## Mobile testing — iOS

Same features as Android (`login.feature`, `events.feature`, `booking_e2e_flow.feature`) — the
Page Object layer is platform-aware internally, so no separate iOS feature files exist. App
under test: `apps/eventhub-app-simulator.app`.

**iOS needs a Simulator-targeted build specifically, not just "the iOS app."** A real-device
`.ipa`/`.app` (built for the `iphoneos` SDK) cannot install on a Simulator regardless of Appium
config — confirmed live, the exact failure is `Simulator architecture is not supported by the
<bundle-id> application`. `apps/eventhub-app-simulator.app` is the correct Simulator build
(`iphonesimulator` SDK, universal `x86_64`/`arm64`).

**Before any iOS command:**

1. Boot an iOS Simulator — e.g. `iPhone 17 Pro` (see `config/mobile-devices.json`).
2. Start Appium: `appium --base-path /wd/hub` — check first whether one's already running
   (`curl -s http://127.0.0.1:4723/wd/hub/status`; a `200` with `"ready":true` means it's
   already up, nothing to start). An `EADDRINUSE`/"address already in use" error from the
   command above means exactly that — port 4723 is already bound to a running Appium server,
   not a broken one. Only kill it (`pkill -f 'appium --base-path'`) if you actually need to
   restart it (e.g. after changing global Appium config); otherwise just leave it running and
   proceed straight to the test command.

```bash
# Sequential, iOS - one flag, nothing else changes
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.platform=ios

# One feature file
mvn test -Dmobile.platform=ios -Dcucumber.features=src/test/resources/features/mobile/login.feature

# One scenario, by (a regex matching) its Gherkin name
mvn test -Dmobile.platform=ios -Dcucumber.filter.name="Valid credentials log in and show the home screen"

# Several feature files
mvn test -Dmobile.platform=ios -Dcucumber.features="src/test/resources/features/mobile/login.feature,src/test/resources/features/mobile/events.feature,src/test/resources/features/mobile/booking_e2e_flow.feature"

# Against a different environment
mvn test -Denv=dev -Dcucumber.filter.tags="@mobile" -Dmobile.platform=ios
```

**This build's login always succeeds regardless of the password typed** — verified live, the
mock backend authenticates any well-formed credentials as one fixed demo account. So
`login.feature`'s negative scenarios are Flutter's own client-side form validation (blank
fields, a malformed email), not a server-rejected wrong password — this is expected behavior,
not a bug, on both platforms.

---

## Mobile — parallel execution (pooled devices)

Device details are **never** passed on the CLI for this — everything comes from
`config/mobile-devices.json`. Whether a run is sequential (one device) or parallel (every
configured device, as a work queue) depends only on whether `-Dparallel` is present -
`MobileDevicePool` hands each thread the next free device and blocks a thread if every device
is currently busy, rather than one device per thread regardless of load:

```bash
# Sequential - one device (mobile.platform picks androidList/iosList's first id)
mvn test -Dcucumber.filter.tags="@mobile"

# Pooled across every configured device (work queue) - existing feature files need no changes
mvn test -Dcucumber.filter.tags="@mobile" -Ddataproviderthreadcount=3

# Narrowed to one platform's list only (no cross-platform mixing)
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.platform=android -Ddataproviderthreadcount=2
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.platform=ios -Ddataproviderthreadcount=2
```

Add a device, or change which devices `androidList`/`iosList` point at, by editing
`config/mobile-devices.json` only — no code change:

```json
{
  "devices": {
    "android1": { "platform": "android", "deviceName": "Pixel_10", "platformVersion": "17" },
    "ios1": { "platform": "ios", "deviceName": "iPhone 17 Pro", "platformVersion": "26.2" },
    "ios2": { "platform": "ios", "deviceName": "iPhone 17", "platformVersion": "26.2" }
  },
  "androidList": ["android1"],
  "iosList": ["ios1", "ios2"]
}
```

For a genuinely concurrent multi-device run, boot **every** emulator/simulator listed in
`androidList`/`iosList` *before* running Appium/the tests — one Appium server
(`appium.server.url`) serves every session, but each device still needs to already be booted.

---

## Mobile — BrowserStack (real devices / cloud)

Same step-definition/Page Object code, unchanged — only the device provider and a few config
values change, so nothing above needs to run differently once this is set up.

```bash
mvn test -Dcucumber.filter.tags="@mobile" -Dmobile.device.provider=BROWSERSTACK \
    -Dmobile.device.name="Samsung Galaxy S23" -Dmobile.platform.version=13 \
    -Dbrowserstack.app.id=bs://<app-id-from-browserstack-upload>
```

1. Add `BROWSERSTACK_USERNAME` / `BROWSERSTACK_ACCESS_KEY` to `.secret.env` (never plain
   config).
2. Upload the app once per version via BrowserStack's own
   [app-upload API](https://www.browserstack.com/docs/app-automate/appium/upload-app) (not
   automated by this framework) — it returns the `bs://<id>` string `browserstack.app.id`
   needs.
3. Optional: `-Dbrowserstack.project.name=...` / `-Dbrowserstack.build.name=...` for dashboard
   grouping.

`config/{env}.properties` deliberately does not ship with `browserstack.*` pre-filled — pass
them as `-D` flags per run, or add persistent lines only if an environment should always target
BrowserStack.

---

## Useful extras

**Everything** (bare `mvn test`, no flags at all — the single `RunCucumberTest` runner is
discovered automatically and every scenario runs, mobile included):

```bash
mvn test
```

**Everything except mobile** (the common "did I break anything" run — no local emulator
required):

```bash
mvn test -Dcucumber.filter.tags="not @mobile"
```

**Rerun only what just failed** (Surefire writes `target/surefire-reports/testng-failed.xml`
after every run):

```bash
mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test
```

Overwritten by the next full run — copy it first if you want to keep retrying a specific
failure while iterating on other tests.

**Switch the test-data source format** (json is default; every surface's data exists in all
four formats today):

```bash
mvn test -Dcucumber.filter.tags="@api" -Dtestdata.format=yaml
mvn test -Dcucumber.filter.tags="@api" -Dtestdata.format=csv
mvn test -Dcucumber.filter.tags="@api" -Dtestdata.format=excel
```

**Masking is off by default** (a local run shows real values as-is — the common case is your
own `.secret.env` already has the value). Turn it on for a run you intend to share (local
only — CI always stays masked regardless of this flag):

```bash
mvn test -Dmasking.enabled=true
# or: MASKING_ENABLED=true mvn test
```

**Choose which report(s) actually get enriched** (default `extent` only — `allure-testng`'s own
bare pass/fail/`@Before`/`@After` capture runs either way, this just controls the *added*
detail: masked request/response, screenshots, page source, etc.):

```bash
mvn test -Dreport.types=allure         # Allure only
mvn test -Dreport.types=extent,allure  # both
```

**Debug from the CLI, attach a remote debugger:**

```bash
mvn test -Dmaven.surefire.debug
# then attach on localhost:5005
```

**Reports/logs after any run:**

- `reports/extent/index.html` — self-contained HTML report (or `reports/extent/report-{timestamp}.html`
  per run if `report.overwrite=false`) — scenario names come from Gherkin, not Java method names
- `reports/api/index.html` — self-contained API report, grouped by resource tag (`@auth`/
  `@events`/`@bookings`/`@system`)
- `allure-results/` — `allure serve allure-results` to view
- `logs/framework.log` — MDC-tagged per thread
- `target/screenshots/` — captured per `screenshot.mode`
- `target/surefire-reports/` — raw TestNG/Surefire output (one entry per scenario, named
  `runScenario` — see `reports/extent`/`reports/api` above for the real scenario names)

**Clean up accumulated local run artifacts** (all git-ignored and regenerated by the next
`mvn test`, so safe to run anytime):

```bash
scripts/clean-local.sh          # allure-results/, allure-report/, test-output/, logs/, reports/
scripts/clean-local.sh --all    # + mvn clean (target/)
```

---

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Mobile fails with `SessionNotCreated` | Two distinct causes, same exception type. **No emulator/Appium server running at all** — start both, or exclude `mobile` (`-Dcucumber.filter.tags="not @mobile"`). **Server running but wrong base path** (message says `Response code 404`, not a connection failure) — Appium is up but not serving `/wd/hub` (its 2.x/3.x default is the bare `/` root); confirm with `curl http://127.0.0.1:4723/wd/hub/status` (should be `200`) and restart with `appium --base-path /wd/hub` if it isn't. |
| `-Dcucumber.filter.tags="@x"` runs zero scenarios but still `BUILD SUCCESS` | `@x` isn't a real tag — see the taxonomy above (`@smoke`/`@sanity`, `@api`/`@web`/`@mobile`, `@positive`/`@negative`/`@e2e`, `@auth`/`@events`/`@bookings`/`@system`). Check the printed test count. |
| `mvn test` (or any tag filter) runs zero tests, no `RunCucumberTest` entry appears in `target/surefire-reports/` at all | Would happen again if the runner class were ever renamed away from Surefire's default `**/*Test.java`/`**/Test*.java` discovery pattern — see `RunCucumberTest`'s own javadoc. Confirm `com.tests.runners.RunCucumberTest` still exists and its name still ends in `Test`. |
| iOS install fails with `Simulator architecture is not supported` | Wrong app binary — use `apps/eventhub-app-simulator.app`, not a real-device `.ipa`/`.app`. |
| A `@Before`/`@After` Cucumber hook seems to have been skipped | Can't happen silently any more — `BeforeMethodAlwaysRunListener` fails the suite at start-of-run with the exact `Class#method` if a plain TestNG `@BeforeMethod` (framework-internal, not a test author's concern) is ever missing `alwaysRun = true`; Cucumber's own `@Before`/`@After` hooks always run regardless of tag filtering by design. |
| A report shows secret values in plain text | Expected by default — masking is off unless enabled. Turn it on for a run you intend to share: `mvn test -Dmasking.enabled=true ...` (or `MASKING_ENABLED=true`). CI always masks regardless of this flag. Two masked values with the same `********-xxxxxxxx` suffix are the same underlying secret. |
| A parallel API run shows one `status="SKIP"`/`retried="true"` scenario in `target/surefire-reports/testng-results.xml` but the run still reports `BUILD SUCCESS` | Not a bug — `RetryAnalyzer` (`retry.max.count`) absorbed a transient failure (a network blip against the live API) and the retry passed; TestNG's own bookkeeping convention records the failed first attempt as `SKIP`. Only a real `BUILD FAILURE` needs investigating. |
| `Log4j2 could not find a logging implementation` | Harmless — Apache POI's internal logger falling back to SimpleLogger. |
| Selenium CDP version warnings | Harmless — Chrome's DevTools Protocol is newer than Selenium's bundled client. |
| Want to rerun just what failed, not the whole run | `mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test` |

For the full framework documentation (architecture, project structure, configuration
precedence, test-data conventions, reporting, CI/CD), see `README.md`.
