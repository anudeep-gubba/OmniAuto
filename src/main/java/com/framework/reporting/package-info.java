/**
 * Extent Reports and Allure Reports integration behind a common reporting facade (Phase 11).
 *
 * <p>{@link com.framework.reporting.ExtentManager} owns the single {@code ExtentReports}
 * instance and the current thread's report node; {@link com.framework.reporting.ExtentLoggingAppender}
 * (wired in {@code logback.xml}, not called directly) mirrors every framework/test log event
 * into it automatically. {@link com.framework.reporting.AllureManager} wraps the couple of
 * Allure attachment operations the framework needs beyond what {@code allure-testng} already
 * does natively. {@link com.framework.reporting.ReportManager} is the one composite operation
 * (a screenshot belongs in both reports) that would otherwise duplicate across callers.</p>
 */
package com.framework.reporting;
