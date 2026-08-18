package com.tests.web.pages;

import com.framework.web.BasePage;
import com.tests.web.components.HeaderComponent;

/**
 * eventhub.rahulshettyacademy.com's post-login landing page (requirement.md
 * &sect;6 example: {@code HomePage} - a genuine fit here, unlike the earlier
 * saucedemo target where "InventoryPage" better matched the real
 * application's own terminology; see RULE 4, adapt naming to what actually
 * exists). The full event listing lives on its own page/URL in the real
 * app, so it gets its own Page Object: {@link EventsPage}.
 */
public class HomePage extends BasePage {

    /**
     * Deliberately not cached as a field: re-located fresh on every call so a
     * client-side re-render of the nav (e.g. after login/logout state
     * changes) never leaves a stale root behind - see Phase 5 summary for
     * the concrete bug this pattern was written to avoid.
     */
    public HeaderComponent header() {
        return new HeaderComponent();
    }

    public boolean isDisplayed() {
        boolean displayed = header().isLoggedIn();
        logger.info("Home page displayed (logged-in nav present): {}", displayed);
        return displayed;
    }
}
