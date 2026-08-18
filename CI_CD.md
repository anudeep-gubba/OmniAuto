# CI/CD

Phase 13 (requirement.md §26). This framework is CI/CD-ready by construction, not by
special-casing: every `-D` flag below is exactly what you already run locally (§12/§22),
and secrets resolve the same way in CI as they do in a developer's `.secret.env` (§13) -
nothing here is framework code, only pipeline configuration around the same `mvn` command.

```
mvn clean test -Denv=qa -Dgroups=regression -Dbrowser=chrome -Dheadless=true
```

**Found in practice, not assumed:** that `-Dgroups=regression` is requirement.md's own
illustrative example - this codebase's tests were never actually tagged with a `regression`
group (the real ones are `smoke`, `sanity`, `api`, `web`, `mobile`, `frameworkSelfTest` - see
`RetryBehaviorTest`'s Javadoc for the last one). A live run of the exact command above
confirmed `-Dgroups=regression` matches **zero tests** and Surefire still reports
`BUILD SUCCESS` - a silent false-green, not a failure you'd notice without checking the
count. All three pipelines below leave `-Dgroups` blank for their "run everything" job
(confirmed live to run all groups correctly) rather than filtering on a group name that
doesn't exist.

Three ready-to-use pipeline definitions ship in this repo:

| Platform | File |
|---|---|
| GitHub Actions | `.github/workflows/ci.yml` |
| Jenkins | `Jenkinsfile` (declarative pipeline) |
| GitLab CI | `.gitlab-ci.yml` |

All three do the same three things: run tests with the standard `-D` flags, exclude
`mobile`/`frameworkSelfTest` by default, and archive `logs/`, `reports/extent/`,
`allure-results/`, and `target/screenshots/` as build artifacts regardless of pass/fail.

**Honesty note on validation**: this repo has no CI platform or remote configured yet
(nothing to push to), so these three configs have been validated the ways that are actually
possible without one - YAML syntax-checked (`ruby -ryaml`), the Jenkinsfile's braces/parens
balance-checked, and every `mvn` command they run is the exact command already proven live,
repeatedly, throughout Phases 1-12. What has **not** been done is an actual run on a real
GitHub Actions/Jenkins/GitLab instance - that can only happen once this repo has a remote
and one of those platforms wired to it.

## Secrets

Real credentials never live in the repo (`.secret.env` is gitignored, RULE 6). Every CI
platform config expects two secrets, resolved by `SecretManager` as CI/CD environment
variables - the highest-precedence tier, above `.secret.env` (which does not exist on any
CI agent anyway):

| Secret | Purpose |
|---|---|
| `EVENTHUB_EMAIL` | eventhub.rahulshettyacademy.com test account email |
| `EVENTHUB_PASSWORD` | eventhub.rahulshettyacademy.com test account password |

Where to configure them:
- **GitHub Actions**: repo Settings → Secrets and variables → Actions → New repository secret.
- **Jenkins**: Manage Jenkins → Credentials, as two "Secret text" credentials with IDs
  `eventhub-email` / `eventhub-password` (the `Jenkinsfile` references these IDs directly).
- **GitLab CI**: Settings → CI/CD → Variables, marked **Masked** and **Protected**.

No framework code change is ever needed to add a new secret this way - any key
`SecretManager.get("KEY")` is asked for resolves against `System.getenv("KEY")` first,
automatically, on every platform.

## `@BeforeMethod` must declare `alwaysRun = true` in this codebase

**Found in practice while validating this phase's CI configs, not assumed** - and the most
significant thing this phase turned up. A live run of the exact command
`mvn clean test -Denv=qa -Dgroups=smoke -Dbrowser=chrome -Dheadless=true` (the CI configs'
own "smoke" job) produced real failures: `EventBookingChainingTest`/`DataDrivenEventCreationTest`
started getting `401 Unauthorized` on calls that had worked in every prior phase. Isolated
with a minimal two-line TestNG reproduction (no framework code involved at all): a
`@BeforeMethod` with no `groups` of its own is silently **skipped** by TestNG whenever a
group *include* filter (`-Dgroups=X` / `<groups><run><include>`) is active, even though the
`@Test` method it exists to set up still runs - producing confusing downstream failures
(here, calls going out unauthenticated) rather than an obvious "setup didn't run" error.
`@BeforeMethod(alwaysRun = true)` fixes it, confirmed with the same minimal reproduction.

Every `@BeforeMethod` in this codebase (`EventsTest`, mobile `LoginTest`/`ProductsTest`,
`EventBookingChainingTest`, `DataDrivenEventCreationTest`, `WebUtilsTest`) now declares
`alwaysRun = true` for this reason. **Any new `@BeforeMethod` added to this framework must
do the same**, or it will work perfectly in every unfiltered run and then silently stop
running the moment someone adds `-Dgroups=` to a CI job or local command - exactly the kind
of failure that's cheap to prevent and expensive to debug after the fact.

## Why `mobile` and `frameworkSelfTest` are excluded by default

- **`mobile`**: requires a running Android emulator/iOS simulator and an Appium server (see
  the local dev setup this mirrors). No shared CI runner has that out of the box. Running
  mobile tests in CI is possible but needs a self-hosted runner with an emulator pre-provisioned,
  or a device-farm integration (BrowserStack/Sauce Labs - explicitly a documented future
  extension point, requirement.md §34, not built here) - out of scope for these three configs.
- **`frameworkSelfTest`**: `RetryBehaviorTest` deliberately contains a test that fails on
  *every* invocation, to prove `RetryAnalyzer` never retries an assertion failure with a real
  assertion rather than a simulated one (see its own Javadoc). Running it in the main
  pipeline would make every build red forever on a passing check, not a regression - run it
  deliberately instead: `mvn test -Dgroups=frameworkSelfTest`.

## Browsers on CI runners

- **GitHub Actions** (`ubuntu-latest`): Chrome and Firefox ship preinstalled. Selenium
  Manager (bundled with Selenium 4, already a dependency here) resolves the matching
  chromedriver/geckodriver automatically at runtime - no separate driver-install step.
- **GitLab CI**: the standard `maven:3.9-eclipse-temurin-17` image has *no* browser, so
  `.gitlab-ci.yml`'s `before_script` installs Chrome directly. Swap this for your org's own
  browser-enabled base image if one exists, or drop the browser-install step entirely for an
  API-only pipeline variant (`-Dgroups=api`).
- **Jenkins**: depends entirely on the agent. A containerized Maven-only image will not have
  a browser; either use/build a custom image that does, or run on a static/VM agent with
  Chrome/Firefox already installed. The `Jenkinsfile`'s header comment flags this explicitly.

## Reports as build artifacts (requirement.md §17/§26)

Every pipeline archives, regardless of pass/fail:

- `target/surefire-reports/` - TestNG's native results **and** JUnit-format XML (Surefire's
  TestNG provider writes both), which is what lets Jenkins' built-in `junit` step work
  without an extra plugin.
- `logs/framework.log` (+ rotated files) - the full action-level log (Phase 10), MDC-tagged
  per test method.
- `reports/extent/index.html` - the self-contained Extent HTML report (Phase 11); download
  and open directly, no server needed.
- `allure-results/` - raw Allure result JSON, not yet a browsable report. Generate/view one:
  - Locally: `allure serve allure-results` (needs the [Allure CLI](https://allurereport.org/docs/gettingstarted/)
    installed - this repo does not bundle an `allure-maven` plugin, deliberately: CI-native
    Allure integrations (the Jenkins Allure plugin already wired into the `Jenkinsfile`'s
    `post` block, or a GitHub Actions/GitLab marketplace report-publishing step) do this job
    better than a Maven plugin would, RULE 16 - don't add a dependency a platform already
    covers.
- `target/screenshots/` - PNGs captured on failure (or every action, per `screenshot.mode`),
  the same files already attached inline into the Extent/Allure reports above.

## Parallel execution in CI (Phase 12/14)

```
mvn test -Dgroups=api -Dparallel=classes -DthreadCount=4
```

No suite XML anywhere in this repo (Phase 14 - see README.md's "Maven commands" and the
"no suite XML" note below): `-Dparallel`/`-DthreadCount` are plain Surefire/TestNG system
properties, confirmed live to genuinely parallelize whatever `-Dgroups`/`-Dtest` already
selected - real Chrome/Firefox sessions and API calls interleaved on different threads at
once. Combine with any group/test selection above; heavier combinations (more threads, Web
groups included) are better suited to a scheduled/nightly job than every PR.

## No suite XML anywhere in this repo (Phase 14)

Every test-selection concern - an individual test, a group, `-DexcludedGroups`, parallel
mode/thread count, browser, environment - is a command-line `-D` flag against Surefire's own
classpath-wide TestNG discovery. Picking a different subset in CI is a different flag value
in the pipeline invocation (or a `workflow_dispatch`/build-parameter input, no pipeline file
edit either), never a suite XML or repo change:

```
mvn test -Dtest=AuthenticationTest                    # one class
mvn test -Dtest=AuthenticationTest#loginWithExistingAccountWorks   # one method
mvn test -Dgroups=smoke                                # one group
mvn test -Dgroups=smoke,api                            # several groups
mvn test -DexcludedGroups=mobile,frameworkSelfTest      # everything except these
```

An earlier version of this framework used two suite XML files plus a `-Pparallel-stress`
Maven profile for the parallel-execution scenario specifically (Phase 12). Removed in Phase
14 once it became clear `-Dparallel`/`-DthreadCount` cover the same need without a file to
maintain - one less thing that needs a repo edit to run a different combination.

## Multiple environments in CI (requirement.md §12)

`-Denv=qa` above is only an example. Any environment with a matching
`config/{env}.properties` file works identically in CI - promote a build through
dev → qa → staging by re-running the same pipeline with a different `-Denv=` value (the
GitHub Actions workflow exposes this as a `workflow_dispatch` input; the Jenkinsfile exposes
it as a build parameter). `ConfigManager` fails fast (requirement.md §31) if the requested
environment's properties file is missing, so a typo here is caught immediately, not
mid-test.
