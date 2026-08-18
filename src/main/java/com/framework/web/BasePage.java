package com.framework.web;

import com.framework.driver.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for every Web Page Object (requirement.md &sect;6). Concrete
 * pages extend this, define their own locators, and expose business-level
 * actions - never {@code driver.findElement(...)} directly (RULE 12):
 *
 * <pre>
 * loginPage.enterUsername(username).enterPassword(password).clickLogin();
 * inventoryPage.verifyDisplayed();
 * </pre>
 *
 * <p>Only the handful of methods nearly every page needs (click, type,
 * getText, isDisplayed, navigateTo) are wrapped here. Anything else -
 * dropdowns, alerts, frames, windows, JS - is called directly via
 * {@link WebActions}/{@link WebUtils}, both public; wrapping every one of
 * their methods here would just be pass-through noise (requirement.md
 * &sect;28: no class/method that adds no real value).</p>
 */
public abstract class BasePage {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected WebDriver driver() {
        return WebDriverManager.getDriver();
    }

    protected void click(By locator) {
        WebActions.click(locator);
    }

    protected void type(By locator, String value) {
        WebActions.type(locator, value);
    }

    protected String getText(By locator) {
        return WebActions.getText(locator);
    }

    protected boolean isDisplayed(By locator) {
        return WebActions.isDisplayed(locator);
    }

    protected void navigateTo(String url) {
        WebUtils.navigateTo(url);
    }
}
