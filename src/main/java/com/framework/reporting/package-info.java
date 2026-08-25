/**
 * Extent Reports and Allure Reports integration behind a common reporting facade (Phase 11),
 * for Web and Mobile - plus a separate, self-contained API report.
 *
 * <p>{@link com.framework.reporting.ExtentManager} owns the single {@code ExtentReports}
 * instance and the current thread's report node; {@link com.framework.reporting.ExtentLoggingAppender}
 * (wired in {@code logback.xml}, not called directly) mirrors every framework/test log event
 * into it automatically. {@link com.framework.reporting.AllureManager} wraps the couple of
 * Allure attachment operations the framework needs beyond what {@code allure-testng} already
 * does natively. {@link com.framework.reporting.ReportManager} is the one composite operation
 * (a screenshot belongs in both reports) that would otherwise duplicate across callers.</p>
 *
 * <p><b>API tests use neither</b> - {@link com.framework.reporting.ApiReportRecorder} collects
 * each API test's requests/responses/assertions into {@link com.framework.reporting.ApiReportModel}
 * records, which {@link com.framework.reporting.ApiHtmlReportRenderer} renders once, at suite
 * end, as one dependency-free {@code reports/api/index.html} - matching the reporting structure
 * of the standalone {@code RestAssuredTestNG} framework this was ported from. See {@code
 * ApiReportRecorder}'s javadoc for why the two are kept fully separate rather than merged.</p>
 */
package com.framework.reporting;
