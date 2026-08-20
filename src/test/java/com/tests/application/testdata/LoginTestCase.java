package com.tests.application.testdata;

import com.framework.testdata.TestCaseMetadata;
import com.framework.testdata.TestCaseRecord;

/**
 * One row of a login test-data file whose shape (just {@code email}/{@code password}) is
 * identical across the surfaces that use it - {@code testdata/json/web/web.json}, {@code
 * testdata/json/android/android.json}, and {@code testdata/json/ios/ios.json} - so it lives here
 * at the {@code testdata} root, shared, rather than duplicated as a near-identical {@code
 * WebLoginTestCase}/{@code MobileLoginTestCase} pair. The JSON files themselves stay separate
 * per surface (see {@link com.tests.application.testdata.TestDataSurface}); only the
 * Java shape is shared. If a surface ever needs a field the others don't (e.g. a mobile-only
 * {@code deviceId}), split it back out into its own record at that point - sharing a type is not
 * a promise the shapes can never diverge.
 *
 * <p>Splits {@code metadata} ({@link TestCaseMetadata}) from the actual {@code data} ({@link
 * LoginData}), matching the JSON's own shape - see {@link TestCaseMetadata}'s javadoc for why.
 * {@code LoginData} is nested here rather than its own file - see {@link
 * com.tests.application.testdata.api.AuthApiTestCase} for why.
 */
public record LoginTestCase(TestCaseMetadata metadata, LoginData data) implements TestCaseRecord<LoginTestCase.LoginData> {

    /** The {@code data} object of one row - see {@link LoginTestCase}. */
    public record LoginData(String email, String password) {
    }
}
