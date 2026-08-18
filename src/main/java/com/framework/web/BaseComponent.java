package com.framework.web;

import com.framework.exceptions.ElementInteractionException;
import com.framework.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for reusable UI components embedded in one or more pages
 * (requirement.md &sect;7) - a header present on every page, or a card
 * repeated once per item in a list.
 *
 * <p>Every locator method here is scoped under {@link #root}, so N instances
 * of the same component (e.g. N product cards) never collide with each
 * other's elements, and locators/actions are written once instead of
 * duplicated per page that embeds the component.</p>
 *
 * <p>Two constructors cover the two ways a component comes into being: a
 * repeated component is handed an already-located {@link WebElement} root by
 * its parent page (which enumerated several via {@link WebActions#findAll});
 * a page-wide singleton component (e.g. a header) locates its own root via a
 * {@link By}, wait-safe like everything else in the framework.</p>
 */
public abstract class BaseComponent {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final WebElement root;

    protected BaseComponent(WebElement root) {
        this.root = root;
    }

    protected BaseComponent(By rootLocator) {
        this.root = WebWaits.waitForVisible(rootLocator);
    }

    protected WebElement root() {
        return root;
    }

    /** Finds an element scoped under this component's root, waiting for it to appear - never a raw, unwaited find (RULE 12). */
    protected WebElement find(By locator) {
        try {
            return WaitUtils.buildFluentWait(root).until(context -> context.findElement(locator));
        } catch (TimeoutException e) {
            throw new ElementInteractionException(
                    "Timed out waiting for '" + locator + "' inside " + getClass().getSimpleName(), e);
        }
    }

    protected String textOf(By locator) {
        return find(locator).getText();
    }

    protected void click(By locator) {
        // find() only waits for the element to exist under root; a React (or similar) app can
        // render it before its handlers attach, so a raw click() right after find() can silently
        // do nothing. Waiting for elementToBeClickable on the already-located element closes that gap.
        WebElement element = find(locator);
        WebWaits.waitFor(ExpectedConditions.elementToBeClickable(element), "element to be clickable: " + locator);
        WebActions.clickResiliently(element, locator.toString());
        logger.info("Clicked '{}' inside {}", locator, getClass().getSimpleName());
    }
}
