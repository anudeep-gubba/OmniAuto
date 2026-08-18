Yes. For an AI coding agent, the prompt should **force it to design first, inspect the repository, avoid assumptions, implement incrementally, and validate every phase**.

I would use the following as your **master prompt**.

```text
You are a Senior QA Automation Architect and Senior Java Automation Engineer.

Your task is to design and implement a production-grade, enterprise-level unified automation framework using:

- Java
- Maven
- TestNG
- Selenium WebDriver
- Appium
- REST Assured
- Extent Reports
- Allure Reports
- SLF4J + Logback

The framework must support Web UI, Mobile UI, and API automation from a single reusable framework.

IMPORTANT:
Do NOT immediately start generating code.

First inspect the existing repository/project structure, understand what already exists, identify reusable components, identify gaps, and then propose the implementation architecture.

Do NOT make assumptions about existing files, classes, packages, dependencies, or functionality.
If something already exists, reuse or improve it instead of creating a duplicate implementation.

==================================================
1. PRIMARY OBJECTIVE
==================================================

Build a single enterprise automation framework capable of:

1. Web automation using Selenium
2. Mobile automation using Appium
3. API automation using REST Assured
4. TestNG-based test execution
5. Parallel execution without thread/data/driver conflicts
6. Multiple environments
7. Centralized environment configuration
8. Secure secret management
9. JSON/YAML/Excel/CSV test-data support
10. Page Object Model for Web and Mobile
11. Service Object Model for APIs
12. API-to-API data chaining
13. Detailed action-level logging
14. Extent Reports
15. Allure Reports
16. Screenshots and failure diagnostics
17. Multiple browsers
18. Multiple resolutions
19. Configurable timeouts
20. TestNG groups, parameters and DataProviders
21. Retry handling
22. CI/CD execution
23. Easy addition of new tests
24. Strong separation between framework code and test code

The final framework should be production-grade, maintainable, scalable and easy for another automation engineer to understand.

==================================================
2. FIRST STEP — REPOSITORY ANALYSIS
==================================================

Before changing anything:

1. Inspect the complete repository structure.
2. Inspect pom.xml.
3. Inspect all existing Java source files.
4. Inspect test resources.
5. Inspect configuration files.
6. Inspect existing TestNG configuration.
7. Inspect existing reporting implementation.
8. Inspect existing utilities.
9. Inspect existing page objects.
10. Inspect existing API classes.
11. Inspect existing test-data handling.
12. Inspect existing listeners.
13. Inspect existing driver management.
14. Inspect existing logging.
15. Identify duplicate functionality.
16. Identify architectural problems.
17. Identify missing capabilities.

Create an architecture assessment containing:

- Existing architecture
- What should be retained
- What should be refactored
- What should be removed
- What is missing
- Recommended target architecture
- Implementation phases
- Risks
- Dependencies

Do not modify code during this assessment phase.

==================================================
3. TARGET ARCHITECTURE
==================================================

The target architecture should follow this conceptual model:

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

The architecture must have strong separation between:

* framework
* test implementation
* test data
* environment configuration
* secrets
* reports
* logs

==================================================
4. RECOMMENDED PACKAGE ARCHITECTURE
   ===================================

Use the following as a target architecture, but adapt it to the existing repository after inspection.

src/main/java/

framework/
config/
constants/
enums/
exceptions/

```
driver/
    DriverFactory
    DriverManager
    WebDriverManager
    MobileDriverManager

web/
    BasePage
    WebActions
    WebWaits
    WebUtils

mobile/
    BaseMobilePage
    MobileActions
    MobileWaits
    MobileUtils

api/
    ApiClient
    ApiRequest
    ApiResponse
    ApiHeaders
    ApiContext
    ApiUtils

    services/
    requests/
    responses/

testdata/
    TestDataManager
    TestDataReader
    JsonDataReader
    YamlDataReader
    ExcelDataReader
    CsvDataReader
    PlaceholderResolver

secrets/
    SecretManager
    SecretResolver
    SensitiveDataMasker

context/
    TestContext
    VariableManager

reporting/
    ExtentManager
    AllureManager
    ReportManager

logging/
    LoggerManager

listeners/
    TestListener
    SuiteListener
    RetryAnalyzer

utils/
    WaitUtils
    JsonUtils
    FileUtils
    DateUtils
    RandomDataUtils
    ScreenshotUtils
```

==================================================
5. WEB AUTOMATION
   =================

Implement Selenium WebDriver support with:

* Chrome
* Firefox
* Edge
* Safari

Support:

* headless/headed execution
* browser selection through configuration
* resolution configuration
* maximize window
* custom viewport/resolution
* page load timeout
* script timeout
* explicit waits
* polling interval
* alert handling
* iframe handling
* window/tab handling
* JavaScript utilities
* keyboard/mouse actions
* dropdown handling
* file upload/download where appropriate
* screenshots

Do NOT encourage Thread.sleep().

All waits must be centralized.

==================================================
6. PAGE OBJECT MODEL
   ====================

Web automation MUST use Page Object Model.

Tests should not contain raw Selenium implementation such as:

driver.findElement(...)

unless there is a strong architectural reason.

Use:

BasePage
|
+-- LoginPage
+-- HomePage
+-- ProductPage
+-- CheckoutPage

Pages should expose business-level actions.

Example:

loginPage
.enterUsername(username)
.enterPassword(password)
.clickLogin();

Do not put assertions everywhere inside page objects unless appropriate.

Keep page objects focused on UI interaction.

==================================================
7. COMPONENT OBJECT MODEL
   =========================

Where useful, support reusable components:

* Header
* Navigation
* Footer
* ProductCard
* Modal
* Table
* Menu

Example:

LoginPage
|
+-- HeaderComponent

HomePage
|
+-- HeaderComponent
+-- ProductComponent

Avoid duplicating locators and actions.

==================================================
8. MOBILE AUTOMATION
   ====================

Implement Appium support for:

* Android
* iOS

The architecture should support:

* device name
* platform name
* platform version
* automation name
* app path
* UDID
* appPackage
* appActivity
* bundleId
* server URL
* capabilities

Use Page Object Model for mobile.

Provide:

BaseMobilePage
MobileActions
MobileWaits
MobileDriverManager

Do not create completely separate frameworks for Android and iOS.

Use common abstractions wherever practical.

==================================================
9. API AUTOMATION
   =================

Implement REST Assured through a framework-level API abstraction.

Architecture:

API Test
|
API Service
|
ApiClient
|
REST Assured

Provide:

* GET
* POST
* PUT
* PATCH
* DELETE
* headers
* query parameters
* path parameters
* request body
* response validation
* authentication
* status code validation
* response extraction
* schema validation where appropriate
* request/response logging
* configurable timeouts
* retry policy where appropriate

Do not put raw REST Assured implementation throughout test classes.

==================================================
10. API SERVICE OBJECT MODEL
    ============================

Create reusable service classes such as:

AuthenticationService
UserService
OrderService
ProductService

The exact services should depend on the actual application/repository.

Tests should be able to write:

userService.createUser(request);

rather than directly implementing REST Assured logic.

==================================================
11. API DATA CHAINING
    =====================

This is a mandatory capability.

The framework must support storing data returned from one API and using it in another API.

Example:

POST /users
|
+--> extract userId
|
+--> store userId
|
GET /users/{userId}

Support values such as:

* token
* userId
* accountId
* orderId
* transactionId
* referenceId

Provide a thread-safe context/variable manager.

Example conceptual API:

apiContext.set("userId", userId);

String userId = apiContext.get("userId");

The implementation must be thread-safe.

Never allow API test execution in one thread to accidentally consume another thread's values.

==================================================
12. ENVIRONMENT MANAGEMENT
    ==========================

Support multiple environments:

* dev
* qa
* uat
* staging
* prod-like environments where appropriate

Example:

config/
dev.properties
qa.properties
uat.properties

Execution:

mvn clean test -Denv=qa

Also support command-line overrides:

mvn clean test -Denv=qa -Dbrowser=chrome -Dheadless=true

Configuration precedence should be:

Default configuration
|
Environment configuration
|
System properties
|
TestNG parameters
|
Test-specific override

Do not hardcode environment URLs in test classes.

==================================================
13. SECRET MANAGEMENT
    =====================

Sensitive information must NEVER be hardcoded in:

* Java code
* test classes
* JSON
* YAML
* CSV
* Excel
* committed properties files

Use a .secret.env file for local development.

Example:

.secret.env

LOGIN_USERNAME=testuser
LOGIN_PASSWORD=secretPassword
API_CLIENT_ID=clientId
API_CLIENT_SECRET=clientSecret

The .secret.env file MUST be added to .gitignore.

The framework must also support CI/CD environment variables so that CI does not require committing .secret.env.

Secret precedence should support:

CI/CD environment variables
|
.secret.env
|
other explicitly configured secure sources

==================================================
14. PLACEHOLDER RESOLUTION
    ==========================

Test data must support placeholders.

Example:

login.json

{
"username": "${{LOGIN_USERNAME}}",
"password": "${{LOGIN_PASSWORD}}"
}

The framework should resolve:

${{LOGIN_USERNAME}}
${{LOGIN_PASSWORD}}

through SecretManager / VariableManager.

Also support runtime variables:

${{userId}}
${{orderId}}
${{accessToken}}

Example:

API 1 returns:

{
"id": 12345
}

Framework stores:

userId = 12345

API 2 test data:

{
"userId": "${{userId}}"
}

The placeholder should resolve automatically.

Do not expose secret values in reports or logs.

==================================================
15. TEST DATA MANAGEMENT
    ========================

Support:

* JSON
* YAML
* Excel
* CSV

Create a common TestDataManager abstraction.

The test should not care whether the source is:

JSON
YAML
Excel
CSV

Example conceptual usage:

testDataManager.load("login.json");

or:

testDataManager.load("users.xlsx");

Integrate with TestNG DataProviders.

Support data-driven tests.

Ensure test data handling is thread-safe during parallel execution.

Do not allow one parallel test to mutate shared test data used by another test.

==================================================
16. LOGGING
    ===========

Use:

SLF4J
Logback

Provide detailed action-level logging.

Examples:

[INFO] Navigating to Login page
[INFO] Entering username
[INFO] Entering password
[INFO] Clicking Login button
[INFO] Waiting for Dashboard
[INFO] Dashboard displayed

API:

[INFO] POST /users
[INFO] Request headers
[INFO] Request body
[INFO] Response status: 201
[INFO] Response body

Mobile:

[INFO] Launching application
[INFO] Finding login button
[INFO] Tapping login button

Sensitive data MUST be masked.

Example:

password=********
Authorization=********
client_secret=********
access_token=********

==================================================
17. REPORTING
    =============

Implement BOTH:

* Extent Reports
* Allure Reports

Do not duplicate reporting implementation throughout test code.

Use TestNG listeners and centralized reporting managers.

Reports must contain:

* test name
* class
* method
* parameters
* environment
* browser
* device
* platform
* start time
* duration
* status
* test steps
* logs
* screenshots
* exceptions
* retry information
* API request
* API response
* assertion details

==================================================
18. ACTION-LEVEL REPORTING
    ==========================

Framework actions should automatically be reportable.

Example:

TEST: Login

1. Navigate to Login page
2. Enter username
3. Enter password
4. Click Login
5. Verify Dashboard
6. Screenshot
7. PASS

The test author should NOT need to manually add reporting code for every framework action.

==================================================
19. SCREENSHOTS
    ===============

Support configurable screenshot modes:

FAILURE
EVERY_ACTION
DISABLED

Example:

screenshot.mode=FAILURE

Screenshots should automatically attach to:

* Extent
* Allure

On failure capture:

* screenshot
* page source where appropriate
* exception
* relevant logs

==================================================
20. PARALLEL EXECUTION
    ======================

Parallel execution is a critical requirement.

Use thread-safe architecture.

WebDriver must use ThreadLocal.

Example conceptual model:

Thread 1 -> Chrome Driver 1
Thread 2 -> Chrome Driver 2
Thread 3 -> Firefox Driver 3

Mobile drivers must also be isolated.

API context must be thread-local.

Reporting context must be thread-safe.

Test data state must not be shared unsafely.

Avoid global mutable static state.

Support TestNG:

parallel="methods"
parallel="classes"
parallel="tests"

where appropriate.

The framework must be tested with parallel execution.

Explicitly identify any components that cannot safely be shared.

==================================================
21. THREAD SAFETY
    =================

Audit every singleton/static object.

Classify objects as:

1. Immutable and globally shareable
2. Thread-safe singleton
3. Thread-local
4. Test-scoped
5. Suite-scoped

Use ThreadLocal where required.

Do NOT blindly make everything static.

Do NOT blindly create a singleton for WebDriver.

==================================================
22. TESTNG
    ==========

Use TestNG for:

* suites
* groups
* parameters
* DataProviders
* listeners
* retry
* dependencies
* parallel execution

Support groups:

smoke
sanity
regression
web
mobile
api

Example:

mvn test -Dgroups=smoke

==================================================
23. RETRY
    =========

Implement a controlled retry analyzer.

Retry should:

* be configurable
* have a maximum retry count
* clearly indicate retry execution in reports
* never hide the original failure
* avoid retrying known assertion/business failures where appropriate

Example:

Initial attempt: FAILED
Retry 1: FAILED
Retry 2: PASSED

Final report should clearly show this history.

==================================================
24. TIMEOUTS
    ============

Centralize:

* implicit wait
* explicit wait
* page load timeout
* script timeout
* API connection timeout
* API socket timeout
* polling interval
* Appium command timeout

Avoid hardcoded timeout values.

==================================================
25. BROWSER AND RESOLUTION
    ==========================

Support:

Chrome
Firefox
Edge
Safari

Support configurable resolutions:

1920x1080
1366x768
1440x900
375x812
390x844

Allow:

-Dbrowser=chrome
-Dresolution=1920x1080

Do not hardcode these values in test classes.

==================================================
26. CI/CD
    =========

The framework must be CI/CD ready.

Support:

Jenkins
GitLab CI
GitHub Actions

The framework must support command-line configuration.

Example:

mvn clean test
-Denv=qa
-Dgroups=regression
-Dbrowser=chrome
-Dheadless=true

Reports and logs should be generated as build artifacts.

==================================================
27. MAVEN
    =========

Use Maven with clean dependency management.

Keep dependencies:

* current and compatible
* minimal
* justified

Do not add libraries simply because they are convenient.

Every dependency must have a reason.

Avoid dependency conflicts.

==================================================
28. CODING STANDARDS
    ====================

Follow:

* SOLID principles
* clean code
* DRY
* composition over unnecessary inheritance
* meaningful naming
* small focused classes
* interfaces where useful
* dependency inversion where appropriate
* proper exception handling
* no duplicated logic
* no hardcoded environment-specific values
* no hardcoded credentials
* no unnecessary static state

Avoid overengineering.

Do not create classes/interfaces that provide no real architectural value.

==================================================
29. TEST CODE DESIGN
    ====================

Test classes should remain simple.

Example conceptual Web test:

@Test
public void validLogin() {

```
LoginData data = testDataManager.get("validLogin");

loginPage
    .enterUsername(data.getUsername())
    .enterPassword(data.getPassword())
    .clickLogin();

homePage.verifyDisplayed();
```

}

Example API test:

@Test
public void createAndGetUser() {

```
Response response =
    userService.createUser(request);

String userId =
    response.jsonPath().getString("id");

apiContext.set("userId", userId);

userService.getUser(
    apiContext.get("userId")
);
```

}

The framework should make tests readable like business workflows.

==================================================
30. EXCEPTIONS
    ==============

Create meaningful framework exceptions where appropriate.

Examples:

FrameworkException
ConfigurationException
DriverInitializationException
TestDataException
SecretResolutionException
ApiException
ApiAuthenticationException
ElementInteractionException

Do not swallow exceptions.

Error messages should clearly identify:

* operation
* test
* environment
* component
* underlying cause

==================================================
31. VALIDATION
    ==============

The framework must validate configuration at startup.

For example:

If env=qa but QA configuration is missing:

FAIL FAST

If browser is unsupported:

FAIL FAST

If required secret is missing:

FAIL FAST

If mobile configuration is incomplete:

FAIL FAST

Do not allow confusing failures later in the test.

==================================================
32. FRAMEWORK STARTUP
    =====================

Implement a predictable startup sequence:

1. Load environment
2. Load configuration
3. Load secrets
4. Validate configuration
5. Initialize reporting
6. Initialize logging
7. Initialize required drivers/resources
8. Execute tests
9. Collect artifacts
10. Cleanup
11. Generate reports

Avoid unnecessary initialization when a test type does not require that resource.

For example, API-only tests should not unnecessarily start a browser.

==================================================
33. CLEANUP
    ===========

Drivers and resources must always be cleaned up.

On test completion:

WebDriver -> quit
Appium Driver -> quit
ThreadLocal -> remove
API context -> clear
temporary resources -> cleanup

Ensure cleanup occurs even when tests fail.

==================================================
34. FRAMEWORK EXTENSIBILITY
    ===========================

The architecture should allow future additions without major redesign.

Potential future capabilities:

* Playwright
* GraphQL
* database validation
* Kafka validation
* Docker
* BrowserStack
* Sauce Labs
* cloud device farms

Do not implement these now unless already required.

Design extension points where appropriate.

==================================================
35. DOCUMENTATION
    =================

Create/update README.md with:

1. Framework overview
2. Architecture
3. Project structure
4. Installation
5. Maven commands
6. Environment configuration
7. Secret configuration
8. Test-data management
9. Web automation example
10. Mobile automation example
11. API automation example
12. API chaining example
13. Parallel execution
14. Reporting
15. CI/CD
16. Adding a new test
17. Adding a new page
18. Adding a new API service
19. Troubleshooting

Include real examples from the implemented framework.

==================================================
36. IMPLEMENTATION STRATEGY
    ===========================

Do NOT implement the entire framework in one uncontrolled change.

Implement in phases.

PHASE 1
Project foundation
Maven
Java
TestNG
package structure
dependencies

PHASE 2
Configuration
environment management
system properties
TestNG parameters
validation

PHASE 3
Secret management
.secret.env
CI environment variables
placeholder resolution
secret masking

PHASE 4
Driver architecture
WebDriver
Appium
ThreadLocal
browser/device configuration

PHASE 5
Web framework
BasePage
Page Objects
WebActions
Waits
screenshots

PHASE 6
Mobile framework
BaseMobilePage
Mobile Page Objects
MobileActions
gestures
waits

PHASE 7
API framework
ApiClient
services
request/response models
authentication

PHASE 8
API context
runtime variables
API chaining
thread-safe data storage

PHASE 9
Test data
JSON
YAML
Excel
CSV
DataProviders
placeholder resolution

PHASE 10
Logging
SLF4J
Logback
action-level logging
sensitive-data masking

PHASE 11
Reporting
Extent
Allure
listeners
screenshots
attachments
retry reporting

PHASE 12
Parallel execution
stress tests
thread-safety audit
resource cleanup

PHASE 13
CI/CD
Maven profiles if required
Jenkins
GitLab
GitHub Actions documentation

PHASE 14
Framework hardening
refactoring
validation
documentation
sample tests
final architecture review

==================================================
37. AFTER EACH PHASE
    ====================

After completing each phase:

1. Compile the project.
2. Run relevant tests.
3. Check for dependency issues.
4. Check for compilation errors.
5. Check for thread-safety issues.
6. Review code quality.
7. Verify no duplicate implementations were introduced.
8. Verify existing functionality was not broken.
9. Update documentation.
10. Summarize changed files.
11. Explain why each change was made.
12. List any remaining risks.

Do not proceed to the next phase if the current phase is broken.

==================================================
38. VALIDATION REQUIREMENTS
    ===========================

At minimum, create/execute representative tests for:

WEB:

* valid login
* invalid login
* multiple browser execution

MOBILE:

* application launch
* basic interaction
* Android configuration

API:

* authentication
* create resource
* extract ID
* use ID in another API

DATA:

* JSON
* YAML
* Excel
* CSV

SECRETS:

* resolve secret placeholder
* verify secret is masked from logs/reports

PARALLEL:

* multiple Web tests
* multiple API tests
* verify contexts do not interfere

REPORTING:

* successful test
* failed test
* screenshot
* API request/response
* retry

==================================================
39. QUALITY GATES
    =================

Do not consider the framework complete unless:

* project compiles
* tests execute successfully
* parallel tests do not conflict
* no credentials are committed
* secrets are masked
* reports are generated
* screenshots are attached on failure
* API chaining works
* JSON/YAML/Excel/CSV work
* multiple environments work
* browser configuration works
* Page Object Model is followed
* API Service Object Model is followed
* drivers are properly cleaned up
* ThreadLocal objects are removed
* README is updated
* no major architectural duplication exists

==================================================
40. IMPORTANT AGENT RULES
    =========================

RULE 1:
Inspect before modifying.

RULE 2:
Never assume a file/class exists.

RULE 3:
Never overwrite existing working functionality without understanding it.

RULE 4:
Reuse existing components where appropriate.

RULE 5:
Do not create duplicate utilities.

RULE 6:
Do not hardcode secrets.

RULE 7:
Do not log secrets.

RULE 8:
Do not use Thread.sleep() as a framework wait strategy.

RULE 9:
Do not use a global static WebDriver.

RULE 10:
Do not use shared mutable state for parallel tests.

RULE 11:
Do not put framework logic inside test classes.

RULE 12:
Do not put raw Selenium/Appium/REST Assured implementation everywhere.

RULE 13:
Tests should be readable and business-oriented.

RULE 14:
Keep Web, Mobile and API implementations separate where platform behavior differs, but share common framework infrastructure.

RULE 15:
Do not overengineer.

RULE 16:
Do not introduce unnecessary dependencies.

RULE 17:
Fail fast for invalid configuration.

RULE 18:
Every framework action should produce useful logs.

RULE 19:
Sensitive values must be masked automatically.

RULE 20:
Every phase must compile and be validated before proceeding.

==================================================
41. REQUIRED INITIAL RESPONSE
    =============================

Before writing any code, respond with ONLY:

1. Repository assessment
2. Current architecture
3. Target architecture
4. Gap analysis
5. Proposed folder structure
6. Dependency changes required
7. Implementation phases
8. Risks and design decisions
9. Files that will be created
10. Files that will be modified
11. Files that should not be changed
12. Validation strategy

Do NOT generate implementation code in the first response.

Wait for approval before implementing Phase 1.

==================================================
42. FINAL GOAL
    ==============

The final result should feel like a framework built by a Senior QA Automation Architect for a large engineering organization.

It must be:

* scalable
* maintainable
* thread-safe
* secure
* reusable
* configurable
* test-data driven
* reportable
* CI/CD ready
* easy for new automation engineers to use

The most important goal is NOT the number of classes.

The most important goal is a clean architecture where a test engineer can add a new Web, Mobile or API test with minimal framework-level changes.

Start by inspecting the repository and provide the architecture assessment only.
Do not generate code until explicitly instructed to proceed.

````


### One recommendation

If you're using an AI coding agent against an **existing repository**, I strongly recommend **not giving it permission to build the whole framework in one shot**.

Use this sequence:

**Agent → inspect → architecture approval → Phase 1 → compile → Phase 2 → compile → ...**

That will dramatically reduce the chance of the agent creating duplicate `DriverManager`, `ConfigManager`, `ApiClient`, reporting, or test-data implementations.

Also, for your `${{...}}` requirement, I would standardize the syntax across the entire framework:

```text
${{LOGIN_USERNAME}}    → secret/environment value
${{LOGIN_PASSWORD}}    → secret/environment value
${{userId}}            → runtime/API context value
${{orderId}}           → runtime/API context value
${{BASE_URL}}          → configuration value
````

This gives you **one variable-resolution mechanism across configuration, secrets, test data, Web/Mobile tests, and API chaining**, which is a strong architectural choice.
