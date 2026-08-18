# Thread-Safety Audit

Phase 12 deliverable (requirement.md §20/§21). Every static/singleton object in
`com.framework.*`, classified per §21's five categories, plus the components that are
*not* safely shareable and why. This is a written consolidation of classifications each
class already documents in its own Javadoc as it was built — see the source for full
reasoning; this file is the at-a-glance index requirement.md §17/§35 (documentation)
asks for. Will be folded into `README.md` in Phase 14.

Categories (requirement.md §21):
1. Immutable and globally shareable
2. Thread-safe singleton
3. Thread-local
4. Test-scoped
5. Suite-scoped

## Framework core

| Class | Field(s) | Category | Why |
|---|---|---|---|
| `ConfigManager` | `globalConfig` | 1 | Loaded once (double-checked lazy init), then an unmodifiable `Map` for the process lifetime. |
| `ConfigManager` | `TESTNG_PARAMETERS`, `TEST_OVERRIDES` | 3 | `ThreadLocal` maps; repopulated before every invoked method by `ConfigParameterListener` (see below - this is the exact mechanism a real bug was in). |
| `SecretManager` | `dotenv` | 1 | `.secret.env` parsed once, then read-only. |
| `SensitiveDataMasker` | `KNOWN_SECRET_VALUES` | 2 | `ConcurrentHashMap.newKeySet()` - safe concurrent registration/lookup from any thread. |
| `PlaceholderResolver` | `SOURCES` | 2 | `CopyOnWriteArrayList` - safe concurrent `registerSource`/`resolve`. |
| `WebDriverManager` / `MobileDriverManager` / `DriverManager` | driver `ThreadLocal`s | 3 | Textbook "one WebDriver per thread" - RULE 9. |
| `VariableManager` | `VARIABLES` | 3 | `ThreadLocal<Map<String,String>>`, the runtime-chaining store underneath `ApiContext`. |
| `ApiContext` | (delegates to `VariableManager`) | 3 | Includes the bearer token, absorbed here in Phase 8. |
| `WebActions`, `MobileActions`, `WebUtils`, `MobileUtils`, `WebWaits`, `MobileWaits`, `ScreenshotUtils`, `ApiClient`, `JsonUtils`, `DriverFactory`, `EnumUtils`, `FileUtils` | none (stateless) | 1 | No mutable fields at all - every value is a local/parameter. Trivially safe. |
| `MobilePortAllocator` | `SYSTEM_PORT_COUNTER`, `WDA_LOCAL_PORT_COUNTER` | 2 | `AtomicInteger`s, only ever read via `getAndIncrement()` (Phase 15) - a brand-new, never-repeated `systemPort`/`wdaLocalPort` on every call, not cached per thread. Concurrent local Android/iOS sessions never collide on a port (requirement.md §20/§34); not used for `MobileDeviceProvider.BROWSERSTACK`, which isolates devices server-side. **Found in practice**: an earlier `ThreadLocal`-cached version (one port reused per thread) failed live against the real emulator - a session that failed to fully start left the device-side UiAutomator2 server still bound to its port, and `RetryAnalyzer`'s retry on the same thread reused the identical cached port and collided with it. Always allocating fresh, never reused, fixed it. See `MobilePortAllocator`'s own Javadoc. |

**Real cross-platform parallel proof (Phase 16)**: `MultiDeviceParallelTest`'s
`@DataProvider(parallel = true)` launched a real Android emulator session and a real iOS
simulator session concurrently. Proven genuinely concurrent (not just "used two threads
sequentially") by reading Appium's own server log directly: the iOS `POST /session` request
and the Android `POST /session` request arrived back-to-back with **zero response logged in
between** - Appium received the second request before it had even finished processing the
first. Both sessions completed successfully (`200`); `MobilePortAllocator` (Android
`systemPort` 8200, iOS `wdaLocalPort` 8100 - different ranges, so no collision even between
different platforms) meant neither blocked the other.
| `AuthenticationService`, `EventService`, `BookingService` | none (stateless) | 1 | Safe to share one instance across parallel threads/tests - all real state lives in `ApiContext`'s ThreadLocal, not the service object. |
| `TestDataManager` | `CACHE` | 2 (structure) / 1 (contents) | `ConcurrentHashMap` of raw, never-mutated-after-parse records; each accessor returns a fresh resolved copy (see `TestData`'s Javadoc) - no caller can corrupt another's view. |
| `ExtentManager` | `EXTENT` | 2 | `ExtentReports.createTest`/`flush` are documented safe for concurrent use - the standard pattern for TestNG parallel + Extent. |
| `ExtentManager` | `CURRENT_TEST` | 3 | One report node per thread at a time. |
| `RetryAnalyzer` | `retryCount` (instance field) | 4 | One `RetryAnalyzer` instance per `@Test` method (assigned by `RetryAnalyzerTransformer`), never shared across methods. |
| `RetryAnalyzer` | `CURRENT_ATTEMPT` | 3 | A deliberately narrow ThreadLocal handoff to `ExtentReportingListener` - see its Javadoc for why that's safe despite not being a queue. |
| All `com.framework.listeners.*` classes | none (stateless) | 5 | One instance per `ServiceLoader` registration, shared for the whole suite - safe because none hold mutable fields; they only read/write *other* classes' thread-local state. |

## Components that are **not** safely shareable (requirement.md §20's explicit ask)

- **Test class instance fields written in `@BeforeMethod`/`@Test` and read in `@AfterMethod`**
  (e.g. `EventBookingChainingTest.createdEventId`, `DataDrivenEventCreationTest.createdEventId`,
  `ApiContextChainingTest.createdEventId`, `EventsTest.eventsPage`). These are safe *only*
  because TestNG guarantees one thread runs one class's before/test/after sequence without a
  second thread concurrently invoking a *different* method on that same shared instance -
  true under `parallel="classes"` (one thread per class) and sequential execution, but would
  **not** be safe if any of these specific classes were run under `parallel="methods"` with
  its own methods invoked concurrently on the shared instance TestNG reuses by default. None
  of them are configured that way; flagging this explicitly is the point of this audit rather
  than leaving it as an implicit assumption.
- **`Thread.currentThread()`-scoped everything above** - by construction, nothing in
  `com.framework` is safe to read/write from a thread other than the one that set it. That is
  the design, not a caveat.

## Two real bugs this audit's classification work already caught (not just theoretical)

1. **Phase 8** - `ApiContextListener` originally cleared `ApiContext` in `ITestListener.onTestStart`,
   which fires *after* `@BeforeMethod` - wiping out a login token `@BeforeMethod` had just set.
   Found by a live run, not code review. See `testng-listener-ordering-gotcha` project note.
2. **Phase 12** - `ConfigParameterListener` had the *identical* bug, undetected since Phase 2:
   `EventsTest` creates its WebDriver inside `@BeforeMethod`, so it was always reading stale
   tier-4 config from whatever test last ran on that pooled thread. Invisible under sequential
   execution; caught immediately by a real `-Dparallel=classes -DthreadCount=4` run -
   `EventsTest`'s Chrome windows were visibly non-headless in a run that asked for
   `headless=true`. Both are now fixed with the same pattern: reset/repopulate unconditionally
   on every invoked method rather than relying on `ITestListener` hooks whose ordering relative
   to `@BeforeMethod`/`@AfterMethod` is easy to get wrong by assumption.

## Real parallel-execution validation (requirement.md §20: "must be tested with parallel execution")

No suite XML anywhere in this repo (Phase 14 - see README.md's "Maven commands"):
`-Dparallel`/`-DthreadCount` are plain Surefire/TestNG system properties, confirmed live to
genuinely parallelize classpath-discovered tests with no suite file at all.

- **`-Dparallel=classes -DthreadCount=4`**: several Web/API test classes running genuinely
  concurrently (real Chrome/Firefox sessions and API calls interleaved on different threads
  at once, confirmed via distinct thread names/overlapping timestamps in `logs/framework.log`).
- **`-Dparallel=methods`**: proven directly by the `invocationCount`/`threadPoolSize` stress
  tests already in `ApiContextChainingTest` and `TestDataManagerTest` - the exact same
  underlying TestNG primitive, exercised at the method level.
- Default regression run (`mvn test -DexcludedGroups=mobile,frameworkSelfTest`) stayed
  green throughout at 115/115.
