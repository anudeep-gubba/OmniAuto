package com.framework.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.framework.reporting.ExtentManager;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;

/**
 * Renders every Gherkin step as its own Given/When/Then/And/But node in the Extent report,
 * nested under the scenario node {@link ExtentReportingListener} already creates - registered as
 * a Cucumber {@code plugin} on {@code RunCucumberTest} (not a TestNG listener: this needs
 * Cucumber's own per-step events, which no TestNG listener is ever told about - a TestNG
 * {@code @Test} invocation is the <em>whole scenario</em>, not one step of it).
 *
 * <p><b>Why this exists:</b> before this class, every scenario collapsed into a single Extent
 * node titled "Test passed."/"Test failed." with a flat stream of framework log lines
 * underneath (page-object/service internals like "Typed into element: By.id: email") - readable
 * enough for the old plain-TestNG suite, where each {@code @Test} method really was one atomic
 * action, but not for a BDD-style report a Gherkin scenario deserves: no visual Given/When/Then
 * breakdown, no way to see which specific step failed without reading the stack trace, no
 * grouping that mirrors the {@code .feature} file the scenario was actually written as.</p>
 *
 * <p>Only {@link PickleStepTestStep}s (real Gherkin steps) get a node - Cucumber's own
 * {@code @Before}/{@code @After} hooks fire as a different event subtype ({@code HookTestStep})
 * and are deliberately skipped here, consistent with {@link ExtentReportingListener}'s own
 * documented scope decision to leave hook-level detail to Allure's native
 * {@code @Before}/{@code @After} sections rather than duplicating it in Extent.</p>
 *
 * <p>Needs no "is this an API scenario" check of its own: {@link ExtentManager#startStep} is a
 * no-op whenever {@link ExtentManager#getTest()} has no scenario-root node to nest under, which
 * is exactly the case for an API scenario ({@link ExtentReportingListener} never calls {@link
 * ExtentManager#startTest} for one - API scenarios get their own self-contained report instead,
 * see {@link ApiTestReportListener}) or for any run with Extent disabled entirely ({@link
 * ExtentManager#startTest} returns {@code null}, so no root is ever pushed). Both cases fall
 * out of the same stack-emptiness check already needed for the ordinary case, rather than a
 * separate tag lookup.</p>
 */
public class CucumberExtentStepListener implements ConcurrentEventListener {

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestStepStarted.class, this::onStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onStepFinished);
    }

    private void onStepStarted(TestStepStarted event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep step)) {
            return;
        }
        String keyword = step.getStep().getKeyword().trim();
        ExtentManager.startStep(keyword, step.getStep().getText());
    }

    private void onStepFinished(TestStepFinished event) {
        if (event.getTestStep() instanceof PickleStepTestStep) {
            ExtentTest stepNode = ExtentManager.getTest();
            if (stepNode != null) {
                finalizeStep(stepNode, event.getResult());
            }
        }
        // Always paired with onStepStarted's push, even for a hook - endStep() is a safe no-op
        // when nothing was actually pushed for this event (see its own javadoc).
        ExtentManager.endStep();
    }

    private static void finalizeStep(ExtentTest stepNode, Result result) {
        switch (result.getStatus()) {
            case PASSED -> stepNode.pass("Step passed.");
            case FAILED, AMBIGUOUS -> {
                if (result.getError() != null) {
                    stepNode.fail(result.getError());
                } else {
                    stepNode.fail("Step failed.");
                }
            }
            case SKIPPED -> stepNode.skip("Step skipped.");
            case PENDING -> stepNode.warning("Step pending - not yet implemented.");
            case UNDEFINED -> stepNode.warning("Step undefined - no matching step definition.");
            default -> { /* UNUSED never reaches a finished scenario's own steps. */ }
        }
    }
}
