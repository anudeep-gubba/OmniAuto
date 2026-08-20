package com.tests.application.testdata;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.enums.MobilePlatformType;
import com.framework.testdata.TestCaseRecord;
import com.framework.testdata.TestDataManager;

/**
 * Every {@code testdata/*} file this repo reads from, one value per surface - the single place
 * that knows the file name each one resolves to, so no test class hardcodes {@code "api/api"}/
 * {@code "web/web"}/etc. (or worse, {@code "api/api.json"} - a hardcoded extension silently
 * defeats {@link TestDataManager}'s YAML/CSV/Excel support for that call site, since a bare name
 * is what lets it fill one in from {@link ConfigKeys#TEST_DATA_FORMAT}).
 *
 * <pre>
 * TestDataSurface.API.getCaseData("validLogin", AuthApiTestCase.class);
 * TestDataSurface.MOBILE_ANDROID.getCaseData("validCredentials", LoginTestCase.class);
 * </pre>
 *
 * <p>{@code MOBILE_ANDROID}/{@code MOBILE_IOS} are separate, explicit values rather than one
 * auto-detecting {@code MOBILE} - a test class that wants "whichever platform this run is
 * actually driving" (the common case) calls {@link #currentMobile()} rather than picking one
 * itself; a test with a genuine reason to pin a specific platform's data regardless of which
 * platform is running can still name it directly.</p>
 */
public enum TestDataSurface {

    WEB("web/web"),
    API("api/api"),
    MOBILE_ANDROID("android/android"),
    MOBILE_IOS("ios/ios");

    private final String fileName;

    TestDataSurface(String fileName) {
        this.fileName = fileName;
    }

    /** The bare file name (no extension) this surface's test data lives in. */
    public String fileName() {
        return fileName;
    }

    /** {@code caseName}'s data from this surface's file - see {@link TestDataManager#getCaseData}. */
    public <D, T extends TestCaseRecord<D>> D getCaseData(String caseName, Class<T> caseType) {
        return TestDataManager.getCaseData(fileName, caseName, caseType);
    }

    /**
     * {@link #MOBILE_ANDROID} or {@link #MOBILE_IOS}, resolved from the same {@link
     * ConfigKeys#MOBILE_PLATFORM} value {@link com.framework.driver.DriverFactory} used to pick
     * Android vs. iOS when the active driver was created - so a mobile test never has to know or
     * hardcode which platform the current run is actually driving.
     */
    public static TestDataSurface currentMobile() {
        MobilePlatformType platform =
                MobilePlatformType.fromString(ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM));
        return platform == MobilePlatformType.ANDROID ? MOBILE_ANDROID : MOBILE_IOS;
    }
}
