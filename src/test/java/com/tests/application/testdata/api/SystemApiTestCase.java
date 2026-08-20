package com.tests.application.testdata.api;

import com.tests.tests.api.SystemApiTest;
import com.framework.testdata.TestCaseMetadata;
import com.framework.testdata.TestCaseRecord;

/**
 * One row of {@code testdata/json/api/api.json} feeding a {@code /health}/{@code /config} case
 * in {@link SystemApiTest}. Splits {@code metadata} ({@link TestCaseMetadata}) from the actual
 * {@code data} ({@link SystemApiData}), matching the JSON's own shape - see {@link
 * com.tests.application.testdata.LoginTestCase} for the identical shared-shape convention.
 * {@code SystemApiData} is nested here rather than its own file - see {@link AuthApiTestCase}
 * for why.
 */
public record SystemApiTestCase(TestCaseMetadata metadata, SystemApiData data) implements TestCaseRecord<SystemApiTestCase.SystemApiData> {

    /**
     * The {@code data} object of one row - see {@link SystemApiTestCase}. Both eventhub system
     * endpoints are unauthenticated and have no meaningful negative case (see
     * {@link SystemApiTest}'s own class javadoc), so every row here is a positive-path
     * expectation only.
     */
    public record SystemApiData(Integer expectedStatusCode, String expectedStatus, String expectedDbStatus) {
    }
}
