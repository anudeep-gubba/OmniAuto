package com.framework.listeners;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestNGMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts a documented TestNG footgun (README "Known limitations") into a loud, immediate
 * failure instead of a silent one: a {@code @BeforeMethod} that declares no {@code groups()} of
 * its own is skipped by TestNG whenever {@code -Dgroups=...} is active, unless it also sets
 * {@code alwaysRun = true} - the {@code @Test} it was meant to set up then simply runs
 * unset-up (e.g. an unauthenticated request failing with a confusing 401 instead of a clear
 * "setup didn't run" error), with nothing in the console or either report pointing at the real
 * cause. Every {@code @BeforeMethod} in this codebase today already sets {@code alwaysRun = true}
 * (see {@code LoginTest}/{@code EventsTest}/{@code EventBookingE2EFlowTest} across all three
 * surfaces) precisely because this was hit once in practice - this listener exists so the next
 * one added without it fails the build at start-of-run instead of relying on code review or
 * institutional memory to catch it.
 *
 * <p>Runs once per suite, before any test executes ({@link #onStart}): reflects over every class
 * that has at least one method participating in this run (i.e. already filtered by whatever
 * {@code -Dgroups}/{@code -Dtest} was passed - a class excluded from this run is not scanned,
 * since a footgun in code that isn't executing this run can't cause this run's silent failure),
 * walks each class's own inheritance chain (catching a {@code @BeforeMethod} inherited from a
 * base class such as {@code BaseWebTest}, not just ones declared directly on the {@code @Test}
 * class), and collects every {@code @BeforeMethod} with empty {@code groups()} and
 * {@code alwaysRun() == false}. Any offender fails the suite immediately with the exact
 * {@code Class#method} list, rather than letting the run continue into a confusing downstream
 * test failure.</p>
 */
public class BeforeMethodAlwaysRunListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        Set<Class<?>> participatingTestClasses = new LinkedHashSet<>();
        for (ITestNGMethod method : suite.getAllMethods()) {
            participatingTestClasses.add(method.getRealClass());
        }

        Set<String> offenders = new TreeSet<>();
        for (Class<?> testClass : participatingTestClasses) {
            collectOffenders(testClass, offenders);
        }

        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start '" + suite.getName() + "': found @BeforeMethod method(s) with no "
                            + "groups() of their own and alwaysRun=false: " + offenders + ". TestNG silently "
                            + "skips these whenever -Dgroups=... is active, letting the @Test it was meant to "
                            + "set up run unset-up. Fix: add alwaysRun = true to each one listed above (or give "
                            + "it matching groups(), if that's actually the intent).");
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        // Nothing to do - the check that matters here only needs to happen once, before start.
    }

    /** Walks {@code testClass}'s own inheritance chain (excluding {@link Object}) for offending {@code @BeforeMethod}s. */
    private void collectOffenders(Class<?> testClass, Set<String> offenders) {
        for (Class<?> current = testClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                BeforeMethod annotation = method.getAnnotation(BeforeMethod.class);
                if (annotation != null && annotation.groups().length == 0 && !annotation.alwaysRun()) {
                    offenders.add(current.getName() + "#" + method.getName());
                }
            }
        }
    }
}
