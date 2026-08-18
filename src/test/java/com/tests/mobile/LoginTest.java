package com.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.mobile.MobileUtils;
import com.tests.mobile.pages.LoginPage;
import com.tests.mobile.pages.ProductsPage;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Phase 6 validation (requirement.md &sect;38 MOBILE checklist: application
 * launch, basic interaction, Android configuration) against
 * apps/swaglabs.apk - the public Sauce Labs SwagLabs demo app - on a local
 * Android emulator (Pixel_10 AVD, real Appium session, no mocking).
 *
 * <p>{@code standard_user}/{@code locked_out_user}/{@code secret_sauce} are
 * this app's own publicly documented sample accounts, tap-to-autofill on its
 * login screen - not real secrets, same reasoning as the earlier saucedemo
 * Web credentials in Phase 5.</p>
 */
public class LoginTest {

    private static final String PASSWORD = "secret_sauce";

    // alwaysRun = true: see EventsTest's identical note - without this, TestNG silently
    // skips this method whenever a group include-filter (-Dgroups=...) is active.
    @BeforeMethod(alwaysRun = true)
    public void launchApp() {
        // Triggers driver creation (and therefore app launch) for this thread.
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    // Also "sanity": see com.tests.web.LoginTest's identical note - one representative live
    // test per surface, the narrowest "is the app fundamentally alive" checkpoint.
    @Test(groups = {"smoke", "sanity", "mobile"})
    public void validLoginNavigatesToProductsPage() {
        LoginPage loginPage = new LoginPage();
        loginPage.enterUsername("standard_user")
                .enterPassword(PASSWORD)
                .tapLogin();

        ProductsPage productsPage = new ProductsPage();
        assertTrue(productsPage.isDisplayed(), "Products page should be displayed after a valid login.");
    }

    @Test(groups = {"smoke", "mobile"})
    public void invalidLoginShowsErrorForLockedOutUser() {
        LoginPage loginPage = new LoginPage();
        loginPage.enterUsername("locked_out_user")
                .enterPassword(PASSWORD)
                .tapLogin();

        assertTrue(loginPage.isErrorDisplayed(), "An error message should be displayed for a locked-out user.");
        assertTrue(loginPage.getErrorMessage().toLowerCase().contains("locked out"));
    }

    @Test(groups = "mobile")
    public void tapToAutofillSampleUserWorks() {
        // Basic-interaction validation (requirement.md section 38): the app's own
        // tap-to-autofill affordance is a real gesture-driven UI interaction distinct
        // from typing directly into the fields (covered by the other two tests here).
        LoginPage loginPage = new LoginPage();
        loginPage.tapSampleUser("standard_user")
                .enterPassword(PASSWORD)
                .tapLogin();

        ProductsPage productsPage = new ProductsPage();
        assertTrue(productsPage.isDisplayed());
    }
}
