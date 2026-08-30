package com.framework.listeners;

import com.framework.utils.TextUtils;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.PickleWrapper;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bridges Gherkin scenario metadata into the TestNG-native reporting listeners that used to read
 * it straight off a plain {@code @Test} method - {@link ExtentReportingListener},
 * {@link ApiTestReportListener}, {@link AllureMetadataListener}.
 *
 * <p>Every scenario now runs through {@code cucumber-testng}'s
 * {@link AbstractTestNGCucumberTests#runScenario(PickleWrapper, io.cucumber.testng.FeatureWrapper)}
 * - one physical Java method, invoked once per scenario via a TestNG {@code @DataProvider} row -
 * so {@code testNgMethod.getGroups()}/{@code .getMethodName()} no longer return a scenario's own
 * tags/name (the method itself carries none; the runner class does), they'd return the runner
 * class's own generic TestNG annotation instead. This class recovers the real values from the
 * {@link PickleWrapper} test parameter every {@code runScenario} invocation carries, so the three
 * listeners above need only swap their extraction call, not their logic. Takes a plain
 * {@link ITestNGMethod} (not {@code IInvokedMethod}) so it works equally from an
 * {@code IInvokedMethodListener} ({@code method.getTestMethod()}) and an {@code ITestListener}
 * ({@code result.getMethod()}).</p>
 *
 * <p>Safe no-op for a non-Cucumber TestNG test (if one is ever added again): every method here
 * falls back to the original {@link ITestNGMethod} reads whenever the invoked method is not
 * {@code runScenario}.</p>
 */
public final class CucumberScenarioSupport {

    private CucumberScenarioSupport() {
    }

    /** {@code true} when this invocation is a Cucumber scenario dispatched via {@code cucumber-testng}. */
    public static boolean isCucumberScenario(ITestNGMethod testNgMethod) {
        return AbstractTestNGCucumberTests.class.isAssignableFrom(testNgMethod.getRealClass());
    }

    /**
     * The scenario's real Gherkin tags (leading {@code @} stripped, lower-cased - matching this
     * project's existing bare-word TestNG group names, e.g. {@code @smoke} -&gt; {@code "smoke"})
     * when this is a Cucumber scenario; otherwise the method's own TestNG {@code @Test(groups=...)}
     * array, unchanged.
     */
    public static List<String> groupsOrTags(ITestNGMethod testNgMethod, ITestResult testResult) {
        PickleWrapper pickle = pickleOf(testResult);
        if (pickle == null) {
            return Arrays.asList(testNgMethod.getGroups());
        }
        List<String> tags = new ArrayList<>();
        for (String tag : pickle.getPickle().getTags()) {
            tags.add(tag.startsWith("@") ? tag.substring(1).toLowerCase() : tag.toLowerCase());
        }
        return tags;
    }

    /**
     * {@code "<Feature> — <Scenario>"} for a Cucumber scenario, otherwise this project's existing
     * {@code "<ClassName> — <humanized method name>"} report title. Used for Extent/API report
     * titles, where two different features can otherwise share an identically-worded scenario
     * name (e.g. "web/events.feature" and "mobile/events.feature" both have generic scenario
     * titles) and need the feature qualifier to stay distinguishable in one flat report list -
     * see {@link #scenarioName} for the bare name Allure's own Story label wants instead (it
     * already has a separate Feature label of its own, so a second copy would just be noise).
     *
     * <p>{@code io.cucumber.testng.Pickle} exposes no literal {@code Feature:} title text (only
     * {@link io.cucumber.testng.Pickle#getUri()}/{@code getName()}/{@code getTags()} - confirmed
     * against the pinned {@code cucumber-testng} jar's own API, not assumed), so "Feature" here
     * is derived from the {@code .feature} file's own path (e.g.
     * {@code classpath:features/web/events.feature} -&gt; {@code "Web Events"}) - a stand-in
     * that still uniquely identifies which file a scenario came from, which is the actual
     * property this needs.</p>
     */
    public static String displayName(ITestNGMethod testNgMethod, ITestResult testResult) {
        PickleWrapper pickle = pickleOf(testResult);
        if (pickle == null) {
            String className = testNgMethod.getRealClass().getSimpleName();
            return className + " — " + TextUtils.humanize(testNgMethod.getMethodName());
        }
        return featureLabel(pickle.getPickle().getUri()) + " — " + pickle.getPickle().getName();
    }

    /**
     * The bare Gherkin scenario name for a Cucumber scenario (no feature qualifier - see
     * {@link #displayName} for why that's a separate method), otherwise the same humanized
     * method-name fallback {@link #displayName} uses.
     */
    public static String scenarioName(ITestNGMethod testNgMethod, ITestResult testResult) {
        PickleWrapper pickle = pickleOf(testResult);
        if (pickle == null) {
            return displayName(testNgMethod, testResult);
        }
        return pickle.getPickle().getName();
    }

    /** {@code classpath:features/web/booking_e2e_flow.feature} -&gt; {@code "Web Booking E2e Flow"}. */
    private static String featureLabel(java.net.URI uri) {
        String[] segments = uri.toString().replace('\\', '/').split("/");
        String file = segments[segments.length - 1].replaceFirst("\\.feature$", "");
        String surface = segments.length >= 2 ? segments[segments.length - 2] : "";
        StringBuilder label = new StringBuilder();
        for (String word : (surface + " " + file).replace('_', ' ').trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private static PickleWrapper pickleOf(ITestResult testResult) {
        Object[] parameters = testResult.getParameters();
        if (parameters.length > 0 && parameters[0] instanceof PickleWrapper pickleWrapper) {
            return pickleWrapper;
        }
        return null;
    }
}
