package com.tests.mobile.components;

import com.framework.exceptions.ElementInteractionException;
import com.framework.mobile.BaseMobileComponent;
import com.framework.mobile.MobileActions;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The header present on the SwagLabs app's Products screen: Menu, Cart (with
 * item-count badge), mirroring the Web layer's {@code HeaderComponent}
 * (requirement.md &sect;7).
 *
 * <p>Rooted directly at the Cart element (verified live: its badge count is
 * a plain {@code TextView} nested directly inside it, no distinct
 * accessibility id of its own) - Menu needs no such scoping, since its own
 * accessibility id is already unique on screen.</p>
 */
public class HeaderComponent extends BaseMobileComponent {

    private static final By MENU_BUTTON = AppiumBy.accessibilityId("test-Menu");
    private static final By CART_BUTTON = AppiumBy.accessibilityId("test-Cart");
    private static final By CART_BADGE = By.className("android.widget.TextView");

    public HeaderComponent() {
        super(CART_BUTTON);
    }

    public void openMenu() {
        MobileActions.tap(MENU_BUTTON);
    }

    public void openCart() {
        tapRoot();
    }

    public boolean isCartBadgeDisplayed() {
        try {
            return find(CART_BADGE).isDisplayed();
        } catch (ElementInteractionException e) {
            return false;
        }
    }

    public String getCartItemCount() {
        return textOf(CART_BADGE);
    }
}
