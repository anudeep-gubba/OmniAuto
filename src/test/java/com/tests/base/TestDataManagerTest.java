package com.tests.base;

import com.framework.api.ApiContext;
import com.tests.api.requests.AuthRequest;
import com.tests.api.requests.CreateEventRequest;
import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.exceptions.TestDataException;
import com.framework.secrets.SecretManager;
import com.framework.testdata.TestData;
import com.framework.testdata.TestDataManager;
import com.framework.utils.RandomDataUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 9 validation for {@link TestDataManager}/{@link TestData}: format-agnostic
 * loading, name/index lookup, typed conversion with scalar coercion, fail-fast error
 * handling, the config-driven default format for extension-less file names
 * ({@link ConfigKeys#TEST_DATA_FORMAT}), and the cached-raw/resolve-on-access
 * thread-safety design (requirement.md &sect;15, &sect;21, &sect;31, &sect;38). No live
 * network calls here - see {@code com.tests.api.DataDrivenLoginTest}/
 * {@code DataDrivenEventCreationTest} for the same data actually driving eventhub's real API.
 */
public class TestDataManagerTest {

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ApiContext.clear();
        ConfigManager.clearThreadState();
    }

    @Test(groups = "smoke")
    public void jsonObjectRootRecordsResolveSecretsAndConvertToADto() {
        TestData data = TestDataManager.load("login.json");
        assertEquals(data.size(), 2);

        Map<String, Object> record = data.get("validLogin");
        assertEquals(record.get("email"), SecretManager.get("EVENTHUB_EMAIL"));

        // Reuses the existing AuthRequest DTO (RULE 5) rather than a test-only lookalike.
        AuthRequest asDto = data.get("validLogin", AuthRequest.class);
        assertEquals(asDto.email(), SecretManager.get("EVENTHUB_EMAIL"));
        assertEquals(asDto.password(), SecretManager.get("EVENTHUB_PASSWORD"));
    }

    @Test(groups = "smoke")
    public void yamlArrayRootSupportsIndexAndNameLookup() {
        TestData data = TestDataManager.load("login-attempts.yaml");
        assertEquals(data.size(), 2);

        assertEquals(data.get(0).get("name"), "validCredentials");
        assertEquals(data.get("wrongPassword").get("password"), "DefinitelyWrongPassword1!");
    }

    @Test(groups = "smoke")
    public void csvRowsConvertIntoATypedDtoWithScalarCoercion() {
        // Every CSV cell is a String; CreateEventRequest.price()/totalSeats() are double/int.
        CreateEventRequest request = TestDataManager.load("events.csv").get("csvEvent1", CreateEventRequest.class);

        assertEquals(request.title(), "CSV Data-Driven Event 1");
        assertEquals(request.price(), 75.0, 0.0001);
        assertEquals(request.totalSeats(), 20);
    }

    @Test(groups = "smoke")
    public void excelRowsConvertIntoATypedDtoWithScalarCoercion() {
        CreateEventRequest request = TestDataManager.load("events.xlsx").get("excelEvent1", CreateEventRequest.class);

        assertEquals(request.title(), "Excel Data-Driven Event 1");
        assertEquals(request.price(), 90.0, 0.0001);
        assertEquals(request.totalSeats(), 25);
    }

    @Test(groups = "smoke")
    public void dataProviderRowsCoverEveryRecord() {
        Object[][] rows = TestDataManager.load("events.csv").dataProvider();
        assertEquals(rows.length, 2);
        assertTrue(rows[0][0] instanceof Map);
    }

    @Test(groups = "smoke")
    public void missingRecordNameFailsFast() {
        TestDataException exception = expectThrows(TestDataException.class,
                () -> TestDataManager.load("login.json").get("noSuchRecord"));
        assertTrue(exception.getMessage().contains("noSuchRecord"));
    }

    @Test(groups = "smoke")
    public void unsupportedExtensionFailsFast() {
        TestDataException exception = expectThrows(TestDataException.class, () -> TestDataManager.load("data.txt"));
        assertTrue(exception.getMessage().contains(".txt"));
    }

    @Test(groups = "smoke")
    public void bareFileNameResolvesAgainstTheConfiguredDefaultFormat() {
        // config/qa.properties sets testdata.format=json, so a bare "login" (no extension)
        // resolves the same way "login.json" does.
        TestData data = TestDataManager.load("login");
        assertEquals(data.size(), 2);
        assertEquals(data.get("validLogin").get("email"), SecretManager.get("EVENTHUB_EMAIL"));
    }

    @Test(groups = "smoke")
    public void bareFileNameFollowsATestOverriddenFormat() {
        ConfigManager.setOverride(ConfigKeys.TEST_DATA_FORMAT, "yaml");

        TestData data = TestDataManager.load("login-attempts");
        assertEquals(data.size(), 2);
        assertEquals(data.get(0).get("name"), "validCredentials");
    }

    @Test(groups = "smoke")
    public void anExtensionInTheCallStillWinsOverTheConfiguredFormat() {
        ConfigManager.setOverride(ConfigKeys.TEST_DATA_FORMAT, "yaml");

        // "login.json" already names an extension, so it is read as JSON regardless of
        // testdata.format - only a bare name defers to the config value.
        TestData data = TestDataManager.load("login.json");
        assertEquals(data.get("validLogin").get("email"), SecretManager.get("EVENTHUB_EMAIL"));
    }

    @Test(groups = "smoke")
    public void unsupportedConfiguredFormatFailsFast() {
        ConfigManager.setOverride(ConfigKeys.TEST_DATA_FORMAT, "xml");

        TestDataException exception = expectThrows(TestDataException.class, () -> TestDataManager.load("login"));
        assertTrue(exception.getMessage().contains("xml"));
    }

    @Test(groups = "smoke")
    public void missingFileFailsFast() {
        expectThrows(TestDataException.class, () -> TestDataManager.load("does-not-exist.json"));
    }

    /**
     * Proves the cached-raw / resolve-on-access design end-to-end (see {@link TestData}'s
     * javadoc): the same cached JSON file's placeholder resolves differently per thread, under
     * real TestNG parallel invocations, because resolution happens fresh on every access
     * against whatever {@link ApiContext} value is current on that thread - not once at
     * load/cache time.
     */
    @Test(groups = "smoke", invocationCount = 20, threadPoolSize = 8)
    public void cachedTestDataResolvesPlaceholdersFreshPerThread() {
        String uniqueValue = "value-" + RandomDataUtils.uniqueId();
        ApiContext.set("stressKey", uniqueValue);

        Map<String, Object> record = TestDataManager.load("context-value.json").get("probe");

        assertEquals(record.get("value"), uniqueValue);
    }
}
