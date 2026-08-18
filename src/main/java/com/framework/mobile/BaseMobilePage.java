package com.framework.mobile;

import com.framework.driver.MobileDriverManager;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for every Mobile Page Object (requirement.md &sect;8), mirroring
 * {@link com.framework.web.BasePage}. Concrete pages extend this, define
 * their own locators, and expose business-level actions - never
 * {@code driver.findElement(...)} directly (RULE 12).
 *
 * <p>Only the handful of methods nearly every page needs (tap, type, getText,
 * isDisplayed) are wrapped here. Anything else - gestures, keyboard, app
 * lifecycle - is called directly via {@link MobileActions}/{@link MobileUtils},
 * both public, for the same reason {@code BasePage} doesn't wrap everything
 * either (requirement.md &sect;28: no method that adds no real value).</p>
 */
public abstract class BaseMobilePage {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected AppiumDriver driver() {
        return MobileDriverManager.getDriver();
    }

    protected void tap(By locator) {
        MobileActions.tap(locator);
    }

    protected void type(By locator, String value) {
        MobileActions.type(locator, value);
    }

    protected String getText(By locator) {
        return MobileActions.getText(locator);
    }

    protected boolean isDisplayed(By locator) {
        return MobileActions.isDisplayed(locator);
    }
}
