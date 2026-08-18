package com.tests.base;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.WebDriverManager;
import com.framework.web.WebActions;
import com.framework.web.WebUtils;
import org.openqa.selenium.By;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Phase 5 validation for {@link WebUtils}/{@link WebActions} capabilities
 * requirement.md &sect;5 explicitly lists: alert handling, iframe handling,
 * window/tab handling, JavaScript utilities, file upload.
 *
 * <p>Each uses a self-contained {@code data:} URL page rather than a real
 * external site, so these are genuine, deterministic, network-independent
 * proofs - not mocks.</p>
 */
public class WebUtilsTest {

    // alwaysRun = true: see EventsTest's identical note - without this, TestNG silently
    // skips this method whenever a group include-filter (-Dgroups=...) is active.
    @BeforeMethod(alwaysRun = true)
    public void startBrowser() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "chrome");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");
        WebDriverManager.getDriver();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    @Test(groups = "web")
    public void alertCanBeAcceptedAndItsTextRead() {
        WebUtils.navigateTo("data:text/html,<html><body>alert-test</body></html>");
        WebUtils.executeScript("window.alert('framework-alert-check');");

        assertEquals(WebUtils.getAlertText(), "framework-alert-check");
        WebUtils.acceptAlert();
    }

    @Test(groups = "web")
    public void alertCanBeDismissed() {
        WebUtils.navigateTo("data:text/html,<html><body>confirm-test</body></html>");
        WebUtils.executeScript(
                "window.confirmResult = window.confirm('dismiss-me') ? 'accepted' : 'dismissed';");

        WebUtils.dismissAlert();

        Object result = WebUtils.executeScript("return window.confirmResult;");
        assertEquals(result, "dismissed");
    }

    @Test(groups = "web")
    public void switchesIntoIframeInteractsAndSwitchesBack() {
        String page = "data:text/html,<html><body>"
                + "<div id='outer'>outer-content</div>"
                + "<iframe id='frame' srcdoc=\"<button id='inner-btn'>inner</button>\"></iframe>"
                + "</body></html>";
        WebUtils.navigateTo(page);

        WebUtils.switchToFrame(By.id("frame"));
        WebUtils.executeScript(
                "document.getElementById('inner-btn').onclick = function() { this.innerText = 'clicked'; };");
        WebActions.click(By.id("inner-btn"));
        assertEquals(WebActions.getText(By.id("inner-btn")), "clicked");

        WebUtils.switchToDefaultContent();
        assertEquals(WebActions.getText(By.id("outer")), "outer-content");
    }

    @Test(groups = "web")
    public void opensNewTabSwitchesToItAndClosesIt() {
        WebUtils.navigateTo("data:text/html,<html><body>original-tab</body></html>");
        String originalHandle = WebUtils.getCurrentWindowHandle();

        WebUtils.openNewTab();
        assertEquals(WebUtils.getWindowHandles().size(), 2, "A second window/tab should now be open.");
        WebUtils.navigateTo("data:text/html,<html><body>new-tab</body></html>");
        assertTrue(WebActions.getText(By.tagName("body")).contains("new-tab"));

        WebUtils.closeCurrentWindow();
        WebUtils.switchToWindow(originalHandle);
        assertEquals(WebUtils.getWindowHandles().size(), 1);
        assertTrue(WebActions.getText(By.tagName("body")).contains("original-tab"));
    }

    @Test(groups = "web")
    public void javaScriptExecutionReturnsAValue() {
        WebUtils.navigateTo("data:text/html,<html><head><title>js-exec-title</title></head><body></body></html>");
        Object title = WebUtils.executeScript("return document.title;");
        assertEquals(title, "js-exec-title");
    }

    @Test(groups = "web")
    public void fileCanBeUploadedThroughAFileInput() throws IOException {
        Path tempFile = Files.createTempFile("framework-upload-test", ".txt");
        Files.writeString(tempFile, "upload content");
        tempFile.toFile().deleteOnExit();

        WebUtils.navigateTo("data:text/html,<html><body><input type='file' id='upload'/></body></html>");
        WebActions.uploadFile(By.id("upload"), tempFile.toAbsolutePath().toString());

        Object uploadedFileName = WebUtils.executeScript(
                "return document.getElementById('upload').files[0].name;");
        assertEquals(uploadedFileName, tempFile.getFileName().toString());
    }
}
