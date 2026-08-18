package com.tests.api;

import com.framework.api.ApiContext;
import com.framework.api.ApiResponse;
import com.framework.api.requests.CreateBookingRequest;
import com.framework.api.requests.CreateEventRequest;
import com.framework.api.responses.BookingResponse;
import com.framework.api.services.AuthenticationService;
import com.framework.api.services.BookingService;
import com.framework.api.services.EventService;
import com.framework.exceptions.FrameworkException;
import com.framework.secrets.SecretManager;
import com.framework.testdata.PlaceholderResolver;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 8 validation for requirement.md &sect;11 ({@code apiContext.set}/
 * {@code get}, thread-safety) and &sect;14 (runtime {@code ${{...}}}
 * placeholders), on top of {@link EventBookingChainingTest}'s Phase 7
 * coverage of the underlying chaining behavior itself.
 */
public class ApiContextChainingTest {

    private final AuthenticationService authService = new AuthenticationService();
    private final EventService eventService = new EventService();
    private final BookingService bookingService = new BookingService();

    private Integer createdEventId;
    private Integer createdBookingId;

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        if (createdBookingId != null) {
            bookingService.cancelBooking(createdBookingId);
            createdBookingId = null;
        }
        if (createdEventId != null) {
            eventService.deleteEvent(createdEventId);
            createdEventId = null;
        }
        authService.logout();
        ApiContext.clear();
    }

    @Test(groups = "api")
    public void getThrowsForAKeyThatWasNeverSet() {
        FrameworkException exception = expectThrows(FrameworkException.class, () -> ApiContext.get("neverSet"));
        assertTrue(exception.getMessage().contains("neverSet"));
    }

    @Test(groups = "api")
    public void setThenGetRoundTrips() {
        ApiContext.set("orderId", "ORD-123");
        assertEquals(ApiContext.get("orderId"), "ORD-123");
        assertTrue(ApiContext.has("orderId"));

        ApiContext.remove("orderId");
        assertFalse(ApiContext.has("orderId"));
    }

    @Test(groups = "api")
    public void placeholderResolverReadsRuntimeVariablesStoredViaApiContext() {
        ApiContext.set("userId", "98765");

        String resolved = PlaceholderResolver.resolve("{\"userId\": \"${{userId}}\"}");

        assertEquals(resolved, "{\"userId\": \"98765\"}");
    }

    /**
     * Direct proof of requirement.md &sect;11's mandatory guarantee: "Never allow API test
     * execution in one thread to accidentally consume another thread's values." Two threads
     * each set the same key to a different value and read it back concurrently; if
     * {@link ApiContext} were backed by shared mutable state instead of a ThreadLocal, one
     * thread would observe the other's value.
     */
    @Test(groups = "api")
    public void contextIsIsolatedPerThreadNotSharedAcrossThreads() throws InterruptedException {
        CountDownLatch bothSet = new CountDownLatch(2);
        CountDownLatch proceedToRead = new CountDownLatch(1);
        AtomicReference<String> threadAValue = new AtomicReference<>();
        AtomicReference<String> threadBValue = new AtomicReference<>();

        Runnable threadA = () -> {
            ApiContext.set("sharedKeyName", "value-from-thread-A");
            bothSet.countDown();
            await(proceedToRead);
            threadAValue.set(ApiContext.get("sharedKeyName"));
            ApiContext.clear();
        };
        Runnable threadB = () -> {
            ApiContext.set("sharedKeyName", "value-from-thread-B");
            bothSet.countDown();
            await(proceedToRead);
            threadBValue.set(ApiContext.get("sharedKeyName"));
            ApiContext.clear();
        };

        Thread t1 = new Thread(threadA);
        Thread t2 = new Thread(threadB);
        t1.start();
        t2.start();
        assertTrue(bothSet.await(5, TimeUnit.SECONDS), "Both threads should have set their value.");
        proceedToRead.countDown();
        t1.join(5000);
        t2.join(5000);

        assertEquals(threadAValue.get(), "value-from-thread-A");
        assertEquals(threadBValue.get(), "value-from-thread-B");
    }

    /**
     * The same isolation guarantee as {@link #contextIsIsolatedPerThreadNotSharedAcrossThreads()},
     * but exercised through TestNG's own parallel-execution machinery (requirement.md &sect;20:
     * "The framework must be tested with parallel execution") rather than raw {@link Thread}s -
     * proof that {@link ApiContextListener}'s before/after-configuration clearing (see its
     * javadoc for why {@code onTestStart} was the wrong hook) does not reintroduce cross-talk
     * when TestNG itself pools and reuses threads across many concurrent invocations.
     */
    @Test(groups = "api", invocationCount = 20, threadPoolSize = 8)
    public void contextIsIsolatedAcrossRealTestNgParallelInvocations() throws InterruptedException {
        String uniqueValue = "value-" + RandomDataUtils.uniqueId();
        ApiContext.set("stressKey", uniqueValue);
        // Widen the window a race would need to land in for another invocation's value to bleed through.
        Thread.sleep(5);
        assertEquals(ApiContext.get("stressKey"), uniqueValue);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The same create-event-then-book flow {@link EventBookingChainingTest} validates, but
     * chained explicitly through {@code ApiContext.set}/{@code get} exactly as requirement.md
     * &sect;29's example test code shows, rather than plain Java fields - and via
     * {@link AuthenticationService}, proving {@link com.framework.api.ApiClient}'s Phase 8
     * refactor to store its bearer token in {@code ApiContext} (under
     * {@link ApiContext#ACCESS_TOKEN_KEY}) still authenticates correctly end-to-end against
     * eventhub's real, live API.
     */
    @Test(groups = {"smoke", "api"})
    public void eventIdChainsThroughApiContextIntoTheBookingCall() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
        assertTrue(ApiContext.has(ApiContext.ACCESS_TOKEN_KEY), "Login should have populated the ApiContext access token.");

        CreateEventRequest eventRequest = new CreateEventRequest(
                "ApiContext Chaining Test Event", "Conference", "Test Venue", "Testville",
                DateUtils.futureIsoDate(30), 100.0, 50);
        ApiResponse createEventResponse = eventService.createEvent(eventRequest);
        createEventResponse.assertStatusCode(201);

        int eventId = createEventResponse.jsonPath().getInt("data.id");
        createdEventId = eventId;
        ApiContext.set("eventId", String.valueOf(eventId));

        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                Integer.parseInt(ApiContext.get("eventId")), "Context Tester", "context.tester@example.com",
                "+91-9876500001", 1);
        ApiResponse createBookingResponse = bookingService.createBooking(bookingRequest);
        createBookingResponse.assertStatusCode(201);

        BookingResponse booking = createBookingResponse.extract("data", BookingResponse.class);
        createdBookingId = booking.id();
        assertEquals(String.valueOf(booking.eventId()), ApiContext.get("eventId"),
                "Booking should reference the event ID chained through ApiContext.");
    }
}
