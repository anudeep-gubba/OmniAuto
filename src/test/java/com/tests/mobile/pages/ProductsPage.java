package com.tests.mobile.pages;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.MobileActions;
import com.tests.mobile.components.HeaderComponent;
import com.tests.mobile.components.ProductItemComponent;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * The SwagLabs app's product listing screen, mirroring the Web layer's
 * {@code InventoryPage}/{@code HomePage} (requirement.md &sect;6).
 */
public class ProductsPage extends BaseMobilePage {

    private static final By PAGE_TITLE = By.xpath("//*[@text='PRODUCTS']");
    private static final By PRODUCT_ITEMS = AppiumBy.accessibilityId("test-Item");

    /**
     * Deliberately not cached as a field: re-located fresh on every call,
     * matching the Web layer's {@code HeaderComponent} pattern - see
     * {@code com.tests.web.pages.HomePage} for the concrete staleness bug
     * that pattern was written to avoid.
     */
    public HeaderComponent header() {
        return new HeaderComponent();
    }

    public boolean isDisplayed() {
        boolean displayed = isDisplayed(PAGE_TITLE);
        logger.info("Products page displayed: {}", displayed);
        return displayed;
    }

    public List<ProductItemComponent> getProducts() {
        List<WebElement> roots = MobileActions.findAll(PRODUCT_ITEMS);
        List<ProductItemComponent> products = roots.stream().map(ProductItemComponent::new).toList();
        logger.info("Products page shows {} item(s)", products.size());
        return products;
    }
}
