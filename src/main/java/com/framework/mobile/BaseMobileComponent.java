package com.framework.mobile;

import com.framework.exceptions.ElementInteractionException;
import com.framework.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for reusable Mobile UI components embedded in one or more pages,
 * mirroring {@link com.framework.web.BaseComponent} (requirement.md &sect;7) -
 * a header present on every screen, or an item card repeated once per
 * product. Kept as a separate, small, mirrored class rather than unifying
 * with {@code BaseComponent} generically: the two constructors' root lookup
 * genuinely needs a different driver manager per platform (RULE 14 - share
 * infrastructure like {@link WaitUtils}, but don't force Web and Mobile
 * through one artificial abstraction where their driver types differ).
 */
public abstract class BaseMobileComponent {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final WebElement root;

    protected BaseMobileComponent(WebElement root) {
        this.root = root;
    }

    protected BaseMobileComponent(By rootLocator) {
        this.root = MobileWaits.waitForVisible(rootLocator);
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

    protected void tap(By locator) {
        WebElement element = find(locator);
        MobileWaits.waitFor(ExpectedConditions.elementToBeClickable(element), "element to be tappable: " + locator);
        element.click();
        logger.info("Tapped '{}' inside {}", locator, getClass().getSimpleName());
    }

    /** For components whose root element is itself the tap target, rather than a child of it. */
    protected void tapRoot() {
        MobileWaits.waitFor(ExpectedConditions.elementToBeClickable(root), "component root to be tappable: " + getClass().getSimpleName());
        root.click();
        logger.info("Tapped root of {}", getClass().getSimpleName());
    }
}
