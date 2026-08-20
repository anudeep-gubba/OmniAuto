package com.tests.application.testdata.mobile;

import com.framework.testdata.TestCaseMetadata;
import com.framework.testdata.TestCaseRecord;
import com.tests.application.testdata.TestDataSurface;

/**
 * One row of {@code testdata/json/android/android.json} or {@code testdata/json/ios/ios.json} - see
 * {@link TestDataSurface#currentMobile()} for which one a given run reads, and {@link TestCaseMetadata}
 * for why {@code metadata} is split from {@code data} ({@link MobileBookingData}), and {@link
 * com.tests.application.testdata.api.AuthApiTestCase} for why {@code MobileBookingData} is
 * nested here rather than its own file. Unlike login ({@link
 * com.tests.application.testdata.LoginTestCase}), this shape has no Web equivalent to share
 * with - it stays Mobile-only.
 */
public record MobileBookingTestCase(TestCaseMetadata metadata, MobileBookingData data) implements TestCaseRecord<MobileBookingTestCase.MobileBookingData> {

    /** The {@code data} object of one row - see {@link MobileBookingTestCase}. */
    public record MobileBookingData(String fullName, String phone) {
    }
}
