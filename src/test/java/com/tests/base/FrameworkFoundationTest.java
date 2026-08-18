package com.tests.base;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Phase 1 validation test.
 *
 * <p>Purpose: prove that Maven, the Java toolchain, and TestNG are wired together
 * correctly (dependencies resolve, compilation succeeds, Surefire discovers and
 * executes TestNG tests). This class carries no framework logic and will be
 * replaced by real Web/Mobile/API smoke tests as later phases land.</p>
 */
public class FrameworkFoundationTest {

    @Test(groups = "smoke")
    public void mavenTestNgWiringIsFunctional() {
        assertTrue(true, "Foundation test executed via TestNG through Maven Surefire.");
    }
}
