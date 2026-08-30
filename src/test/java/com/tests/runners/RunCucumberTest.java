package com.tests.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * The single entry point Surefire's own classpath-wide TestNG discovery picks up automatically
 * - runs every scenario under {@code features/**} (Web, API, Mobile alike) as a TestNG
 * {@code @Test} invocation via {@code cucumber-testng}'s {@link AbstractTestNGCucumberTests}.
 *
 * <p><b>Named {@code RunCucumberTest}, not {@code CucumberTestRunner}, for a real reason, not
 * style:</b> Surefire's own default test-class discovery (used here since there is no
 * {@code <suiteXmlFiles>}/{@code <includes>} configured - see {@code pom.xml}) only considers
 * classes matching {@code **&#47;Test*.java}, {@code **&#47;*Test.java}, {@code **&#47;*Tests.java},
 * or {@code **&#47;*TestCase.java} - a name ending in {@code Runner} is silently skipped by a bare
 * {@code mvn test} entirely (found live during this migration: {@code CucumberTestRunner} ran
 * fine under an explicit {@code -Dtest=CucumberTestRunner}, since that flag bypasses the naming
 * filter, but a plain {@code mvn test}/{@code -Dcucumber.filter.tags=...} with no {@code -Dtest}
 * silently discovered and ran nothing at all). {@code RunCucumberTest} is also Cucumber's own
 * documented convention for exactly this reason.</p>
 *
 * <p>Deliberately one runner, not one per surface: every step-definition class's own Gherkin
 * text is already surface-distinct (see e.g. {@code com.tests.steps.api.CommonApiSteps}'s
 * javadoc), so loading all three surfaces' glue together carries no ambiguous-step risk - and
 * one runner means surface selection is <em>purely</em> a tag expression, never a class name.
 * No {@code tags} here either: scenario selection is entirely {@code -Dcucumber.filter.tags}
 * (e.g. {@code -Dcucumber.filter.tags="@smoke and @web"}), read automatically by
 * {@code cucumber-testng} - the same "everything is a command-line flag, no suite XML" approach
 * this project's {@code -Dgroups}/{@code -DexcludedGroups} always used.</p>
 *
 * <p>Mobile parallel execution (pooled across real devices via
 * {@code com.framework.driver.MobileDevicePool}) runs on this same runner too, not a separate
 * one - driven by the same {@link #scenarios()} data-provider parallelism below
 * ({@code -Ddataproviderthreadcount=N}), not {@code -Dparallel}/{@code -DthreadCount} (see that
 * override's own javadoc for why those two flags have no effect on this suite at all).</p>
 *
 * <p><b>{@link #scenarios()} override is load-bearing, not decorative.</b> Disassembling the
 * pinned {@code cucumber-testng} jar confirms {@code AbstractTestNGCucumberTests.scenarios()}
 * carries a bare {@code @DataProvider} with no attributes - {@code parallel} defaults to
 * {@code false}. TestNG only spreads one method's own data-provider-driven invocations across
 * {@code -Dparallel=methods -DthreadCount=N}'s thread pool when that provider itself opts in;
 * with a single physical {@code @Test} method ({@link #runScenario}) for the entire suite, every
 * scenario would otherwise run on one thread regardless of {@code -Dparallel}, silently
 * defeating every "parallel" command documented in {@code instructions.md}/{@code README.md}
 * and the concurrency guarantees {@code MobileDevicePool}/{@code MobilePortAllocator} exist to
 * provide. Audit finding, verified against the actual bytecode, not assumed from Cucumber's own
 * docs - the earlier device-matrix-only runner this project used before consolidating to one
 * class had this override; the consolidation dropped it.</p>
 *
 * <p>{@code com.framework.listeners.CucumberExtentStepListener} in {@code plugin} above is what
 * gives Extent its own Given/When/Then breakdown per scenario, instead of one flat "Test
 * passed."/"Test failed." node with raw framework log lines underneath - see that class's own
 * javadoc for why a Cucumber {@code plugin}, not another TestNG listener, is what's needed for
 * per-step (not per-scenario) events.</p>
 */
@CucumberOptions(
        features = "classpath:features",
        glue = {"com.tests.steps.web", "com.tests.steps.api", "com.tests.steps.mobile", "com.tests.hooks"},
        plugin = {"pretty", "com.framework.listeners.CucumberExtentStepListener"}
)
public class RunCucumberTest extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
