package com.framework.utils;

import com.aventstack.extentreports.Status;
import com.framework.reporting.ExtentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.util.Objects;

/**
 * Drop-in replacement for the handful of {@code org.testng.Assert} static methods this codebase
 * actually calls ({@code assertTrue}/{@code assertFalse}/{@code assertEquals}/{@code assertNotNull}) -
 * same names, same signatures, so switching a test class over is a one-line import change, no
 * call-site edits (RULE 5: one place, not per-call-site).
 *
 * <p><b>Why this exists:</b> a bare TestNG assertion is invisible in either report while it
 * passes - nothing records that a check even happened, only the test's own final pass/fail
 * summary. On failure, {@code ExtentReportingListener} does show the thrown {@code
 * AssertionError} on the test's own summary line, but not inline in the step sequence next to
 * whatever action led up to it - a multi-assertion test's report reads as "did some things,
 * then failed" with no record of which check actually failed relative to the others, or that
 * every check before it had passed. This wraps each call so both reports get one explicit
 * PASS/FAIL step per assertion, in the same place it happened.</p>
 *
 * <p><b>Delegates the actual comparison to {@code org.testng.Assert} unconditionally</b> -
 * this class never re-implements equality/null/array semantics itself, so a passing/failing
 * assertion behaves byte-for-byte like calling {@code org.testng.Assert} directly (same
 * {@link AssertionError} type and message, same retry/report integration downstream). The
 * try/catch below exists purely to log before rethrowing, never to change the outcome.</p>
 *
 * <p><b>Call with a message whenever you can</b> - {@code assertTrue(x, "Login should report
 * success")} reads far better in a report than the no-message overload ever can. Java gives no
 * way to recover a boolean expression's source text at runtime, so the message-less overloads'
 * generated text is necessarily generic ({@code "Condition should be true"}); to still make two
 * failures in the same method distinguishable without a real message, it's suffixed with the
 * exact call site ({@code (line 47)}) rather than left bare.</p>
 *
 * <p><b>{@link #assertEquals(Object, Object)}'s generated message is asymmetric</b> - found in
 * practice, {@code "Expected quantity but got quantity"} on a <em>passing</em> row reads as a
 * contradiction (identical values either side of "but") even though nothing is wrong; a
 * reader's first reaction is "wait, did this fail?" A pass instead says
 * {@code "Values match: quantity"}; only an actual mismatch shows the expected-vs-actual form,
 * where the two differing values are the point. The equality pre-check that picks between them
 * is for message selection only - {@link Objects#equals} does not need to agree with
 * {@code org.testng.Assert}'s own comparison in every edge case (arrays, for instance) for this
 * to be safe, since the real assertion below is unconditionally delegated to {@code
 * org.testng.Assert} either way; worst case a rare edge case picks the "match" phrasing on a
 * message that then immediately fails with the real, accurate TestNG message instead. No
 * bracket delimiters around either value (unlike TestNG's own native {@code "expected [X] but
 * found [Y]"} wording) - they only earn their keep disambiguating an empty string or stray
 * whitespace, and add visual clutter to the ordinary case of two short, unambiguous tokens.</p>
 *
 * <p><b>The extra {@code int}/{@code Integer}/{@code long} overloads exist for a real reason,
 * not just completeness</b> - audit finding, verified live: {@code assertEquals(names.stream()
 * .distinct().count(), names.size(), "...")} started failing with {@code "expected 3 but got 3"}
 * (the same count on both sides) the moment its call site switched from {@code org.testng.Assert}
 * to this class. {@code count()} returns {@code long}, {@code size()} returns {@code int}; against
 * {@code org.testng.Assert} directly - which, like most of its overloaded methods, declares a
 * full matching set of {@code int}/{@code Integer}/{@code long}/... combinations, not just one
 * {@code Object} catch-all - Java resolves that pair to {@code assertEquals(long, long, String)}
 * via primitive widening (int -&gt; long), comparing the two numbers directly. This class, with
 * only an {@code (Object, Object, String)} signature to offer at the time, forced both arguments
 * to box instead (to {@code Long}/{@code Integer}) - and {@code Long.equals(Integer)} is
 * {@code false} unconditionally, regardless of numeric value, because {@code Object.equals} also
 * checks the runtime type. Adding just {@code (long, long)} to fix that then made a second,
 * different call site ambiguous - {@code assertEquals(int, Integer)} (a raw {@code int} from an
 * API response next to a {@code ThreadLocal<Integer>.get()}, a real, common shape in this
 * codebase) matched both the new {@code (long, long)} (via unboxing then widening) and the
 * existing {@code (Object, Object)} (via boxing) equally validly, with neither more specific -
 * genuinely ambiguous, not a fixable coincidence. The full {@code int}/{@code Integer} overload
 * set below (mirroring exactly what {@code org.testng.Assert} itself declares for this pair)
 * gives every real call shape in this codebase an exact, unambiguous, phase-1 match again -
 * restoring, not just patching, byte-for-byte parity with the delegate this class wraps.</p>
 */
public final class Verify {

    private static final Logger LOGGER = LoggerFactory.getLogger(Verify.class);

    private Verify() {
    }

    public static void assertTrue(boolean condition) {
        assertTrue(condition, "Condition should be true" + callerLocation());
    }

    public static void assertTrue(boolean condition, String message) {
        run(message, () -> Assert.assertTrue(condition, message));
    }

    public static void assertFalse(boolean condition) {
        assertFalse(condition, "Condition should be false" + callerLocation());
    }

    public static void assertFalse(boolean condition, String message) {
        run(message, () -> Assert.assertFalse(condition, message));
    }

    public static void assertEquals(Object actual, Object expected) {
        assertEquals(actual, expected, equalsMessage(Objects.equals(actual, expected), actual, expected));
    }

    public static void assertEquals(Object actual, Object expected, String message) {
        run(message, () -> Assert.assertEquals(actual, expected, message));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - not redundant with the {@code Object} overload above. */
    public static void assertEquals(long actual, long expected) {
        assertEquals(actual, expected, equalsMessage(actual == expected, actual, expected));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - not redundant with the {@code Object} overload above. */
    public static void assertEquals(long actual, long expected, String message) {
        run(message, () -> Assert.assertEquals(actual, expected, message));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - not redundant with {@link #assertEquals(long, long)}. */
    public static void assertEquals(int actual, int expected) {
        assertEquals(actual, expected, equalsMessage(actual == expected, actual, expected));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - not redundant with {@link #assertEquals(long, long, String)}. */
    public static void assertEquals(int actual, int expected, String message) {
        run(message, () -> Assert.assertEquals(actual, expected, message));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - the shape a {@code ThreadLocal<Integer>.get()} paired with a raw {@code int} takes. */
    public static void assertEquals(Integer actual, int expected) {
        assertEquals(actual, expected, equalsMessage(actual != null && actual == expected, actual, expected));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - the shape a {@code ThreadLocal<Integer>.get()} paired with a raw {@code int} takes. */
    public static void assertEquals(Integer actual, int expected, String message) {
        run(message, () -> Assert.assertEquals(actual, expected, message));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - the mirror image of {@link #assertEquals(Integer, int)}. */
    public static void assertEquals(int actual, Integer expected) {
        assertEquals(actual, expected, equalsMessage(expected != null && actual == expected, actual, expected));
    }

    /** See the class javadoc's "int/Integer/long overloads" note - the mirror image of {@link #assertEquals(Integer, int, String)}. */
    public static void assertEquals(int actual, Integer expected, String message) {
        run(message, () -> Assert.assertEquals(actual, expected, message));
    }

    /** Shared by every {@code assertEquals} default-message overload above - see the class javadoc's asymmetric-message note. */
    private static String equalsMessage(boolean equal, Object actual, Object expected) {
        return equal
                ? "Values match: " + actual + callerLocation()
                : "Expected " + expected + " but got " + actual + callerLocation();
    }

    public static void assertNotNull(Object object) {
        assertNotNull(object, "Object should not be null" + callerLocation());
    }

    public static void assertNotNull(Object object, String message) {
        run(message, () -> Assert.assertNotNull(object, message));
    }

    /**
     * {@code " (line 47)"} - the nearest stack frame outside this class, i.e. the actual test
     * method's own call site. Only used to decorate the generated default messages above (never
     * appended to a caller-supplied message - a real message doesn't need it and appending would
     * just be noise); lets two message-less assertions in the same method still read as two
     * distinct report rows instead of two identical, indistinguishable ones.
     */
    private static String callerLocation() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            // Skip Thread.getStackTrace()'s own frame too, not just Verify's - frame 0 is
            // always java.lang.Thread, so without this the very first "non-Verify" frame found
            // was Thread's own internals, not the real caller (found in practice: produced
            // nonsense like "line 2450" pointing into Thread.java, not the test method).
            if (!className.equals(Thread.class.getName()) && !className.equals(Verify.class.getName())) {
                return " (line " + frame.getLineNumber() + ")";
            }
        }
        return "";
    }

    /**
     * Runs {@code assertion}, logging one PASS/FAIL step either way, then rethrows on failure -
     * see class javadoc. A failure also gets its full stack trace logged as its own row
     * ({@link ExtentManager#logStackTrace}) right where it happened, not just the one-line
     * message - the message says what was expected; the trace says where in the call chain it
     * actually broke.
     */
    private static void run(String message, Runnable assertion) {
        try {
            assertion.run();
            LOGGER.info("Assertion passed: {}", message);
            ExtentManager.logAssertion(Status.PASS, message);
        } catch (AssertionError e) {
            LOGGER.error("Assertion failed: {}", message, e);
            ExtentManager.logAssertion(Status.FAIL, message);
            ExtentManager.logStackTrace(e);
            throw e;
        }
    }
}
