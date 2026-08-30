package com.tests.steps.shared;

import com.framework.secrets.SecretManager;
import com.tests.application.services.AuthenticationService;
import com.tests.application.services.BookingService;
import com.tests.application.services.EventService;
import com.tests.application.services.SystemService;

/**
 * One instance per scenario (Cucumber + {@code cucumber-picocontainer} - constructor-injected
 * into every {@code com.tests.steps.api.*} step-definition class and {@code
 * com.tests.hooks.ApiHooks} that need it, so they all share the same services/state within one
 * scenario) - the composition-based replacement for the old inheritance-based
 * {@code BaseApiTest}.
 *
 * <p>{@link #createdEventId}/{@link #createdBookingId} mirror what {@code BaseApiTest}
 * subclasses used to track via a {@code tearDownTestData()} override (itself a {@code
 * ThreadLocal} - see e.g. the original {@code EventApiTest}/{@code BookingApiTest}/{@code
 * EventBookingE2EFlowTest} javadoc for why): here a plain field is enough, since Cucumber
 * creates a fresh {@link ApiScenarioContext} per scenario rather than TestNG's one-instance-
 * shared-across-methods-under-parallelism behavior that made the {@code ThreadLocal} necessary
 * in the first place.</p>
 */
public class ApiScenarioContext {

    public final AuthenticationService authService = new AuthenticationService();
    public final EventService eventService = new EventService();
    public final BookingService bookingService = new BookingService();
    public final SystemService systemService = new SystemService();

    /** An event/booking this scenario created, for {@code ApiHooks} to release afterward. Null when nothing needs cleanup. */
    public Integer createdEventId;
    public Integer createdBookingId;

    /** Logs in as eventhub's shared seeded account (the {@code EVENTHUB_EMAIL}/{@code EVENTHUB_PASSWORD} secrets). */
    public void loginWithSeededAccount() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    }
}
