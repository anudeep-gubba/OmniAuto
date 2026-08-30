package com.tests.hooks;

import com.framework.config.ConfigManager;
import io.cucumber.java.After;

/**
 * Runs after every {@code @web} scenario, regardless of outcome - the Cucumber-hook equivalent
 * of the old {@code BaseWebTest.baseWebCleanup()} {@code @AfterMethod}.
 */
public class WebHooks {

    @After("@web")
    public void clearWebThreadState() {
        ConfigManager.clearThreadState();
    }
}
