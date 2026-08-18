package com.framework.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.annotations.ITestAnnotation;
import org.testng.internal.annotations.DisabledRetryAnalyzer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Assigns {@link RetryAnalyzer} to every {@code @Test} method automatically, so requirement.md
 * &sect;23's retry behavior applies framework-wide without every test author writing
 * {@code @Test(retryAnalyzer = RetryAnalyzer.class)} themselves (matches this project's
 * standing goal of minimal per-test boilerplate - requirement.md &sect;1 item 23 "easy addition
 * of new tests").
 *
 * <p>Only fills in a retry analyzer that is not already set, so a test explicitly declaring
 * its own {@code retryAnalyzer} keeps it unchanged.</p>
 *
 * <p><b>Found in practice, not assumed:</b> {@code ITestAnnotation.getRetryAnalyzerClass()}
 * does <em>not</em> return {@code null} for a {@code @Test} with no {@code retryAnalyzer}
 * declared - TestNG 7.10.2 defaults it to the sentinel
 * {@link DisabledRetryAnalyzer DisabledRetryAnalyzer.class}. A first version of this class
 * checked for {@code null} and silently never assigned {@link RetryAnalyzer} to anything - a
 * live run of a test designed to prove retry actually happens caught it immediately (it
 * failed instead of retrying and passing). Comparing against
 * {@code DisabledRetryAnalyzer.class} instead of {@code null} is the fix.</p>
 *
 * <p>Auto-registered via {@code META-INF/services/org.testng.ITestNGListener}
 * ({@link IAnnotationTransformer} is itself an {@code ITestNGListener} subtype, discovered
 * the same {@link java.util.ServiceLoader} way as every other listener in this package).</p>
 */
public class RetryAnalyzerTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        Class<? extends IRetryAnalyzer> existing = annotation.getRetryAnalyzerClass();
        if (existing == null || existing == DisabledRetryAnalyzer.class) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }
}
