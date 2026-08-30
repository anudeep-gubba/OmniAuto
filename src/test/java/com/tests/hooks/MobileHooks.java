package com.tests.hooks;

import com.framework.config.ConfigManager;
import com.tests.steps.shared.MobileScenarioContext;
import io.cucumber.java.After;

/**
 * Runs after every {@code @mobile} scenario (device-matrix scenarios included), regardless of
 * outcome - the Cucumber-hook equivalent of the old {@code BaseMobileTest.baseMobileCleanup()}
 * {@code @AfterMethod}: release any bookings the scenario left behind (mirroring {@code
 * EventBookingE2EFlowTest}'s own {@code tearDownTestData()} override - a no-op here whenever
 * {@link MobileScenarioContext#myBookingsPage} was never set), then clear thread-local config
 * state.
 */
public class MobileHooks {

    private final MobileScenarioContext context;

    public MobileHooks(MobileScenarioContext context) {
        this.context = context;
    }

    @After("@mobile")
    public void tearDownMobileTestData() {
        if (context.myBookingsPage != null) {
            context.myBookingsPage.clearAllBookingsIfPresent();
            context.myBookingsPage = null;
        }
        ConfigManager.clearThreadState();
    }
}
