/**
 * Test-case data records for {@code testdata/json/api/api.json} - each pairs a shared {@link
 * com.framework.testdata.TestCaseMetadata} (id/name) with a nested {@code *Data} record (see e.g.
 * {@link com.tests.application.testdata.api.AuthApiTestCase.AuthApiData}), used by {@link
 * com.tests.steps.api.AuthSteps}/{@link com.tests.steps.api.EventSteps}/{@link
 * com.tests.steps.api.BookingSteps}/{@link com.tests.steps.api.BookingE2EFlowSteps}. Kept apart from
 * {@link com.tests.requests}/{@link com.tests.responses} (the API's own wire-format DTOs) since
 * these describe a test case, not an HTTP payload.
 */
package com.tests.application.testdata.api;
