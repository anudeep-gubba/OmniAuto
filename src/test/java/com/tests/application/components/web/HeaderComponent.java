package com.tests.application.components.web;

import com.framework.secrets.SensitiveDataMasker;
import com.framework.web.BaseComponent;
import org.openqa.selenium.By;

/**
 * The nav bar present on every eventhub.rahulshettyacademy.com page after
 * login: Home/Events/My Bookings links, the logged-in user's email, and
 * Logout (requirement.md &sect;7 example: {@code LoginPage/HomePage + HeaderComponent}).
 */
public class HeaderComponent extends BaseComponent {

    private static final By HOME_LINK = By.id("nav-home");
    private static final By EVENTS_LINK = By.id("nav-events");
    private static final By BOOKINGS_LINK = By.id("nav-bookings");
    private static final By USER_EMAIL_DISPLAY = By.id("user-email-display");
    private static final By LOGOUT_BUTTON = By.id("logout-btn");

    public HeaderComponent() {
        super(By.cssSelector("nav"));
    }

    public void goToEvents() {
        click(EVENTS_LINK);
        logger.info("Clicked 'Events' in the nav");
    }

    public void goToBookings() {
        click(BOOKINGS_LINK);
        logger.info("Clicked 'My Bookings' in the nav");
    }

    public void goToHome() {
        click(HOME_LINK);
        logger.info("Clicked 'Home' in the nav");
    }

    public void logout() {
        click(LOGOUT_BUTTON);
        logger.info("Logged out");
    }

    public String getLoggedInUserEmail() {
        String email = textOf(USER_EMAIL_DISPLAY);
        logger.info("Logged-in user email shown in nav: {}", SensitiveDataMasker.mask(email));
        return email;
    }

    public boolean isLoggedIn() {
        try {
            return !find(USER_EMAIL_DISPLAY).getText().isBlank();
        } catch (com.framework.exceptions.ElementInteractionException e) {
            return false;
        }
    }
}
