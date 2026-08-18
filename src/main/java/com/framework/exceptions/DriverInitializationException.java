package com.framework.exceptions;

/**
 * Thrown when a {@link org.openqa.selenium.WebDriver} or
 * {@link io.appium.java_client.AppiumDriver} cannot be created: an invalid
 * resolution string, incomplete mobile app identification (no app path, and
 * no package+activity / bundle id), or a failure raised by Selenium/Appium
 * itself while starting a session.
 */
public class DriverInitializationException extends FrameworkException {

    public DriverInitializationException(String message) {
        super(message);
    }

    public DriverInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
