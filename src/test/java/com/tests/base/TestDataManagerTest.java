package com.tests.base;

import com.framework.api.ApiContext;
import com.framework.api.requests.AuthRequest;
import com.framework.api.requests.CreateEventRequest;
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
 * handling, and the cached-raw/resolve-on-access thread-safety design (requirement.md
 * &sect;15, &sect;21, &sect;31, &sect;38). No live network calls here - see
 * {@code com.tests.api.DataDrivenLoginTest}/{@code DataDrivenEventCreationTest} for the
 * same data actually driving eventhub's real API.
 */
public class TestDataManagerTest {

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ApiContext.clear();
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
