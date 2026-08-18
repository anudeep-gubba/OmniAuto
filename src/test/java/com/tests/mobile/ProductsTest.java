package com.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.mobile.MobileUtils;
import com.tests.mobile.components.ProductItemComponent;
import com.tests.mobile.pages.LoginPage;
import com.tests.mobile.pages.ProductsPage;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Phase 6 validation for the Mobile Component Object Model: {@link com.tests.mobile.components.HeaderComponent}
 * as a page-wide singleton component, {@link ProductItemComponent} as an
 * N-repeated component, mirroring the Web layer's equivalent (requirement.md
 * &sect;7), on the real SwagLabs app running in the emulator.
 */
public class ProductsTest {

    private ProductsPage productsPage;

    // alwaysRun = true: see EventsTest's identical note - without this, TestNG silently
    // skips this method whenever a group include-filter (-Dgroups=...) is active.
    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();

        LoginPage loginPage = new LoginPage();
        loginPage.enterUsername("standard_user").enterPassword("secret_sauce").tapLogin();
        productsPage = new ProductsPage();
        assertTrue(productsPage.isDisplayed());
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    @Test(groups = "mobile")
    public void addingProductToCartUpdatesHeaderBadge() {
        assertFalse(productsPage.header().isCartBadgeDisplayed(),
                "Cart badge should not be shown before adding any product.");

        List<ProductItemComponent> products = productsPage.getProducts();
        assertTrue(products.size() > 0, "Products page should list at least one item.");
        products.get(0).addToCart();

        assertTrue(productsPage.header().isCartBadgeDisplayed());
        assertEquals(productsPage.header().getCartItemCount(), "1");
    }

    @Test(groups = "mobile")
    public void eachProductItemIsIndependentlyScoped() {
        // Proves BaseMobileComponent's root-scoping: reading N items' names never
        // returns the same element twice or bleeds one item's data into another's.
        List<ProductItemComponent> products = productsPage.getProducts();
        assertTrue(products.size() >= 2, "Test needs at least 2 products to prove independent scoping.");

        List<String> names = products.stream().map(ProductItemComponent::getName).toList();
        assertEquals(names.stream().distinct().count(), names.size(),
                "Every product item should report its own distinct name.");
    }
}
