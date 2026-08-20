package com.framework.testdata;

/**
 * The {@code metadata} object every test-case-data record in a {@code testdata/*} file carries
 * alongside its actual {@code data}:
 *
 * <pre>
 * "validLogin": {
 *   "metadata": { "testCaseId": "TC-WEB-LOGIN-001", "testCaseName": "Valid login navigates to home page" },
 *   "data": { "email": "${{EVENTHUB_EMAIL}}", "password": "${{EVENTHUB_PASSWORD}}" }
 * }
 * </pre>
 *
 * <p>{@code testCaseName} is a readable business/scenario name (what a test case management
 * tool would show), not a restatement of the Java method name. Neither field carries assertion
 * value of its own - they exist purely so a test failure, an Extent/Allure report entry, or
 * someone skimming the JSON file can identify which test case a row belongs to at a glance.
 * {@link TestDataManager#getCaseData} logs both automatically, so a test case record pairing
 * this with its {@code data} (via {@link TestCaseRecord}) never needs its own logging line.</p>
 */
public record TestCaseMetadata(String testCaseId, String testCaseName) {
}
