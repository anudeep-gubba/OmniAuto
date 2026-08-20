# Running the tests — API, Web, Mobile (Android & iOS)

Command reference for running this suite. Everything here is plain `mvn`/TestNG flags — there
is no suite XML in this repo, so picking a subset is never a file edit. See `README.md` for the
full framework documentation; this file is just the "what do I actually type" cheat sheet.

## Contents

1. [One-time setup](#one-time-setup)
2. [API testing](#api-testing)
3. [Web testing](#web-testing)
4. [Mobile testing — Android](#mobile-testing--android)
5. [Mobile testing — iOS](#mobile-testing--ios)
6. [Mobile — parallel / device matrix](#mobile--parallel--device-matrix)
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
`-Denv=dev` to any command to target `dev` instead). Real TestNG groups this suite tags tests
with: `smoke`, `sanity`, `api`, `web`, `mobile`. A group name nothing is tagged with matches
zero tests but still reports `BUILD SUCCESS` — check the printed test count.

---

## API testing

No browser, no emulator — just the live API (`api.base.url` in `config/{env}.properties`).
Real classes: `AuthApiTest`, `EventApiTest`, `BookingApiTest`, `SystemApiTest`,
`EventBookingE2EFlowTest`.

```bash
# Every API test
mvn test -Dgroups=api

# Just the smoke-tagged API tests (fast subset)
mvn test -Dgroups=smoke,api

# One class
mvn test -Dtest=AuthApiTest

# One method
mvn test -Dtest=AuthApiTest#loginWithExistingAccountWorks

# Several classes at once
mvn test -Dtest=AuthApiTest,EventBookingE2EFlowTest

# Parallel - one class per thread
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4

# Parallel - one method per thread (faster, more concurrent load on the API)
mvn test -Dgroups=api -Dparallel=methods -DthreadCount=8

# Against a different environment
mvn test -Denv=dev -Dgroups=api
```

**The one API sanity check** (fastest possible "is the API even up" signal, no auth/test data
needed — `SystemApiTest`'s `/health` check):

```bash
mvn test -Dgroups=sanity -DexcludedGroups=mobile
```

---

## Web testing

Real classes: `LoginTest`, `EventsTest` (against eventhub.rahulshettyacademy.com).

```bash
# Every Web test
mvn test -Dgroups=web

# Just the smoke-tagged Web tests
mvn test -Dgroups=smoke,web

# One class
mvn test -Dtest=LoginTest

# One method
mvn test -Dtest=LoginTest#validLoginNavigatesToHomePage

# Several classes
mvn test -Dtest=LoginTest,EventsTest

# Choose a browser explicitly (default: chrome)
mvn test -Dgroups=web -Dbrowser=firefox -Dheadless=true
# browser: chrome | firefox | edge | safari (safari can't run headless)

# Parallel - one class per thread
mvn test -Dgroups=web -Dparallel=classes -DthreadCount=4 -Dheadless=true

# Against a different environment
mvn test -Denv=dev -Dgroups=web -Dbrowser=chrome -Dheadless=true
```

**Cross-browser coverage is not a per-test loop** — it's CI's own job matrix (`smoke`/
`regression` in `.github/workflows/ci.yml` run the *entire* Web suite once per browser, as
separate parallel jobs). Running the same test twice locally with two different `-Dbrowser`
values is how you reproduce that locally if needed; there's no single command that runs both at
once on purpose.

---

## Mobile testing — Android

Real classes: `LoginTest`, `EventsTest`, `EventBookingE2EFlowTest`,
`MultiDeviceParallelTest` (device-matrix infra, covered separately below). App under test:
`apps/eventhub-app-release.apk` (bundles an `x86_64` slice alongside device ABIs, so this one
file installs on both a real device and an emulator).

**Before any mobile command:**

1. Boot an Android emulator (AVD) — e.g. `Pixel_10` (the device this suite is tuned against;
   see `config/mobile-devices.json`).
2. Start Appium: `appium --base-path /wd/hub`

```bash
# Sequential, Android (the default platform - no extra flag needed)
mvn test -Dgroups=mobile

# One class
mvn test -Dgroups=mobile -Dtest=LoginTest

# One method
mvn test -Dgroups=mobile -Dtest=LoginTest#validCredentialsLogInAndShowHomeScreen

# Several classes
mvn test -Dgroups=mobile -Dtest=LoginTest,EventsTest,EventBookingE2EFlowTest

# Against a different environment
mvn test -Denv=dev -Dgroups=mobile
```

Android is the default because `mobile.platform=android` is what `config/qa.properties` /
`config/dev.properties` ship with — switch the *default* platform by editing that one property,
no `-D` flag or code change needed for a whole team to standardize on iOS instead. An explicit
`-Dmobile.platform=android` on the command line always works too, for a one-off.

---

## Mobile testing — iOS

Same classes as Android (`LoginTest`, `EventsTest`, `EventBookingE2EFlowTest`) — the Page
Object layer is platform-aware internally, so no separate iOS test classes exist. App under
test: `apps/eventhub-app-simulator.app`.

**iOS needs a Simulator-targeted build specifically, not just "the iOS app."** A real-device
`.ipa`/`.app` (built for the `iphoneos` SDK) cannot install on a Simulator regardless of Appium
config — confirmed live, the exact failure is `Simulator architecture is not supported by the
<bundle-id> application`. `apps/eventhub-app-simulator.app` is the correct Simulator build
(`iphonesimulator` SDK, universal `x86_64`/`arm64`).

**Before any iOS command:**

1. Boot an iOS Simulator — e.g. `iPhone 17 Pro` (see `config/mobile-devices.json`).
2. Start Appium: `appium --base-path /wd/hub`

```bash
# Sequential, iOS - one flag, nothing else changes
mvn test -Dgroups=mobile -Dmobile.platform=ios

# One class
mvn test -Dgroups=mobile -Dmobile.platform=ios -Dtest=LoginTest

# One method
mvn test -Dgroups=mobile -Dmobile.platform=ios -Dtest=LoginTest#validCredentialsLogInAndShowHomeScreen

# Several classes
mvn test -Dgroups=mobile -Dmobile.platform=ios -Dtest=LoginTest,EventsTest,EventBookingE2EFlowTest

# Against a different environment
mvn test -Denv=dev -Dgroups=mobile -Dmobile.platform=ios
```

**This build's login always succeeds regardless of the password typed** — verified live, the
mock backend authenticates any well-formed credentials as one fixed demo account. So
`LoginTest`'s negative cases are Flutter's own client-side form validation (blank fields, a
malformed email), not a server-rejected wrong password — this is expected behavior, not a bug,
on both platforms.

---

## Mobile — parallel / device matrix

Device details are **never** passed on the CLI for these — everything comes from
`config/mobile-devices.json`. Whether a run is sequential or parallel depends only on whether
`-Dparallel` is present.

```bash
# Pooled across every configured device (work queue - whichever device finishes first
# picks up the next queued test; existing classes need no changes to participate)
mvn test -Dgroups=mobile -Dparallel=methods -DthreadCount=3

# The SAME test on every device in a named matrix, concurrently (not a work queue)
mvn test -Dtest=MultiDeviceParallelTest#appLaunchesOnEachDeviceInTheMatrixConcurrently   # "cross-platform" matrix: 1 Android + 1 iOS
mvn test -Dtest=MultiDeviceParallelTest#appLaunchesOnEachIosSimulatorConcurrently        # "ios" matrix: 2 iOS simulators at once
```

Add a device, a new matrix, or change which devices `androidList`/`iosList` point at, by
editing `config/mobile-devices.json` only — no code change:

```json
{
  "devices": {
    "android1": { "platform": "android", "deviceName": "Pixel_10", "platformVersion": "17" },
    "ios1": { "platform": "ios", "deviceName": "iPhone 17 Pro", "platformVersion": "26.2" },
    "ios2": { "platform": "ios", "deviceName": "iPhone 17", "platformVersion": "26.2" }
  },
  "androidList": ["android1"],
  "iosList": ["ios1", "ios2"],
  "matrices": { "cross-platform": ["android1", "ios1"], "ios": ["ios1", "ios2"] }
}
```

For a genuinely concurrent multi-device run, boot **every** emulator/simulator listed in the
matrix/lists you're targeting *before* running Appium/the tests — one Appium server
(`appium.server.url`) serves every session, but each device still needs to already be booted.

---

## Mobile — BrowserStack (real devices / cloud)

Same test/Page Object code, unchanged — only the device provider and a few config values
change, so nothing above needs to run differently once this is set up.

```bash
mvn test -Dgroups=mobile -Dmobile.device.provider=BROWSERSTACK \
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

**Everything except mobile** (the common "did I break anything" run — no local emulator
required):

```bash
mvn test -DexcludedGroups=mobile
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
mvn test -Dgroups=api -Dtestdata.format=yaml
mvn test -Dgroups=api -Dtestdata.format=csv
mvn test -Dgroups=api -Dtestdata.format=excel
```

**Debug a real value that's masked in the report** (local only — CI always stays masked
regardless of this flag):

```bash
mvn test -Dtest=... -Dmasking.enabled=false
```

**Debug from the CLI, attach a remote debugger:**

```bash
mvn test -Dtest=... -Dmaven.surefire.debug
# then attach on localhost:5005
```

**Reports/logs after any run:**

- `reports/extent/index.html` — self-contained HTML report
- `allure-results/` — `allure serve allure-results` to view
- `logs/framework.log` — MDC-tagged per thread
- `target/screenshots/` — captured per `screenshot.mode`
- `target/surefire-reports/` — raw TestNG/Surefire output

---

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Mobile fails with `SessionNotCreated` | No emulator/Appium server running — start both, or exclude `mobile`. |
| `-Dgroups=X` runs zero tests but still `BUILD SUCCESS` | `X` isn't a real group tag. Real ones: `smoke`, `sanity`, `api`, `web`, `mobile`. Check the printed test count. |
| iOS install fails with `Simulator architecture is not supported` | Wrong app binary — use `apps/eventhub-app-simulator.app`, not a real-device `.ipa`/`.app`. |
| `@BeforeMethod`-driven setup silently doesn't run under `-Dgroups=X`, test fails with 401 | Missing `alwaysRun = true` on that `@BeforeMethod` (framework code only - not something a test author adds). |
| A report shows `********-xxxxxxxx` and you need the real value to debug a failure | `mvn test -Dmasking.enabled=false ...` — local only. Two masked values with the same suffix are the same underlying secret, even without unmasking. |
| `Log4j2 could not find a logging implementation` | Harmless — Apache POI's internal logger falling back to SimpleLogger. |
| Selenium CDP version warnings | Harmless — Chrome's DevTools Protocol is newer than Selenium's bundled client. |
| Want to rerun just what failed, not the whole run | `mvn -Dsurefire.suiteXmlFiles=target/surefire-reports/testng-failed.xml test` |

For the full framework documentation (architecture, project structure, configuration
precedence, test-data conventions, reporting, CI/CD), see `README.md`.
