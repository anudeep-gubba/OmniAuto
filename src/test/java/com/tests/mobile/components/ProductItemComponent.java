package com.tests.mobile.components;

import com.framework.mobile.BaseMobileComponent;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * One repeated product item on the SwagLabs app's Products screen
 * (requirement.md &sect;7 example: {@code ProductCard}). Mirrors the Web
 * layer's {@code ProductCardComponent}: the parent page hands each instance
 * an already-located root element, so N items never collide with each
 * other's names/prices/Add-to-cart taps.
 */
public class ProductItemComponent extends BaseMobileComponent {

    private static final By NAME = AppiumBy.accessibilityId("test-Item title");
    private static final By PRICE = AppiumBy.accessibilityId("test-Price");
    private static final By ADD_TO_CART_BUTTON = AppiumBy.accessibilityId("test-ADD TO CART");

    public ProductItemComponent(WebElement root) {
        super(root);
    }

    public String getName() {
        return textOf(NAME);
    }

    public String getPrice() {
        return textOf(PRICE);
    }

    public void addToCart() {
        String name = getName();
        tap(ADD_TO_CART_BUTTON);
        logger.info("Added '{}' to cart", name);
    }
}
