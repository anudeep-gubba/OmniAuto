package com.tests.mobile;

/**
 * One row of {@code testdata/json/mobile-login.json}. {@code testCaseId}/{@code testCaseName}/
 * {@code description} carry no assertion value of their own - they exist purely so a test
 * failure, an Extent/Allure report entry, or someone skimming the JSON file can identify which
 * test case a row belongs to at a glance, independently of the Java method name.
 */
public record MobileLoginTestCase(
        String testCaseId,
        String testCaseName,
        String description,
        String email,
        String password) {
}
