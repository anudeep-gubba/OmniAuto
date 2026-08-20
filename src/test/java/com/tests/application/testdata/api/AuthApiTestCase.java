package com.tests.application.testdata.api;

import com.tests.tests.api.AuthApiTest;
import com.tests.tests.api.EventApiTest;
import com.tests.tests.api.EventBookingE2EFlowTest;
import com.framework.testdata.TestCaseMetadata;
import com.framework.testdata.TestCaseRecord;

/**
 * One row of {@code testdata/json/api/api.json} feeding an {@code /auth/*} case in
 * {@link AuthApiTest} (and the odd auth step inside {@link EventApiTest}/{@link
 * EventBookingE2EFlowTest} that registers its own throwaway account). Splits {@code metadata}
 * ({@link TestCaseMetadata}) from the actual {@code data} ({@link AuthApiData}), matching the
 * JSON's own shape - see {@link com.tests.application.testdata.LoginTestCase} for the identical
 * shared-shape convention (there, one record spans Web and Mobile since their login shape is
 * byte-for-byte identical; here, {@code AuthApiData} stays API-only since its extra {@code
 * token} field and raw-request-body semantics don't apply to the other surfaces).
 *
 * <p>{@code AuthApiData} is nested here rather than its own top-level file: the two are always
 * loaded and read together, and Java only allows one public top-level type per file anyway once
 * both need to be visible outside this package - nesting keeps the pair as one file to create
 * or update instead of two.</p>
 */
public record AuthApiTestCase(TestCaseMetadata metadata, AuthApiData data) implements TestCaseRecord<AuthApiTestCase.AuthApiData> {

    /**
     * The {@code data} object of one row - see {@link AuthApiTestCase}. Not every case needs
     * every field (e.g. a case whose email must be freshly randomized per run leaves
     * {@code email} unset here and generates it in Java instead) - the unused ones are simply
     * absent from that row's JSON and resolve to {@code null}.
     *
     * <p>{@code expectedStatusCode}/{@code expectedError}/{@code expectedField}/
     * {@code expectedMessage} are the response-shape assertions each case expects, same
     * reasoning as {@link BookingApiTestCase.BookingApiData}. Not used by every case: a case
     * exercised only via {@code expectThrows} against a framework-level exception message
     * (e.g. {@code loginWrongPassword}) leaves these unset, since that message is
     * {@code ApiAuthenticationException}'s own generic prefix, not something the server
     * returned.</p>
     */
    public record AuthApiData(
            String email,
            String password,
            String token,
            Integer expectedStatusCode,
            String expectedError,
            String expectedField,
            String expectedMessage) {
    }
}
