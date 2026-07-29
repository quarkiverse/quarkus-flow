package io.quarkiverse.flow.deployment.test.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.acme.ConditionalWorkflow;
import org.acme.HelloWorkflow;
import org.acme.ParallelWorkflow;
import org.acme.ScorePayload;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import io.quarkus.test.QuarkusDevModeTest;

public class WorkflowDiagramEditorRoundTripIT {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            HelloWorkflow.class,
                            ParallelWorkflow.class,
                            ConditionalWorkflow.class,
                            ScorePayload.class)
                    .addAsResource(new StringAsset(
                            "wiremock.url=http://localhost:9999\n"),
                            "application.properties"));

    static final String DEV_UI_URL = "http://localhost:8080/q/dev-ui/quarkus-flow/workflows";

    static Playwright playwright;
    static BrowserContext browserContext;
    Page page;

    @BeforeAll
    static void startPlaywright() {
        playwright = Playwright.create();
        browserContext = playwright.chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setChromiumSandbox(false)
                        .setArgs(List.of("--disable-gpu")))
                .newContext();
    }

    @AfterAll
    static void stopPlaywright() {
        if (browserContext != null) {
            browserContext.close(); // flushes video files
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        page = browserContext.newPage();
    }

    @AfterEach
    void closePage() {
        page.close();
    }

    // -------------------------------------------------------------------------
    // diagramEditorId values are derived from _generateDiagramEditorId in
    // qwc-flow-workflows.js:
    //   "diagramEditor-" + namespace.replaceAll('.', '-') + "-" + name + "-" + version.replaceAll('.', '-')
    // FlowWorkflowBuilder.workflow(name) defaults:
    //   namespace = io.serverlessworkflow.types.Defaults.DEFAULT_NAMESPACE = "org-acme"
    //   version   = io.serverlessworkflow.types.Defaults.DEFAULT_VERSION   = "0.0.1"
    //
    // Task data-testid auto-naming:
    //   set(Map.of(...)) without a name → auto-name "set-0"  → "set-node-/do/0/set-0"
    //   switchWhenOrElse(pred, ...) without a name → auto-name "switch-0" → "switch-node-/do/0/switch-0"
    //   fork("checkInventoryAndCredit", ...) named → "fork-node-/do/0/checkInventoryAndCredit"
    // -------------------------------------------------------------------------
    static Stream<Arguments> javaDslWorkflowCases() {
        return Stream.of(
                // label, diagramEditorId, taskTestId, taskName
                Arguments.of(
                        "hello-java",
                        "diagramEditor-org-acme-hello-0-0-1",
                        "set-node-/do/0/set-0",
                        "set-0"),
                Arguments.of(
                        "parallel-java",
                        "diagramEditor-org-acme-parallel-workflow-using-branches-0-0-1",
                        "fork-node-/do/0/checkInventoryAndCredit",
                        "checkInventoryAndCredit"),
                Arguments.of(
                        "conditional-java",
                        "diagramEditor-org-acme-conditional-routing-0-0-1",
                        "switch-node-/do/0/switch-0",
                        "switch-0"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("javaDslWorkflowCases")
    @DisplayName("editor renders Java DSL workflow and re-renders without side-effects")
    void editorRendersJavaDslWorkflow(
            String label,
            String diagramEditorId,
            String taskTestId,
            String taskName) {

        page.navigate(DEV_UI_URL);

        // Step 1 — eye button for this workflow is visible
        String buttonSelector = "#see-" + diagramEditorId;
        page.waitForSelector(buttonSelector);

        // Step 2 — click eye button; wait for dialog to attach (it is inside a Vaadin
        // overlay and never becomes "visible" in Playwright's sense while animating)
        page.locator(buttonSelector).click();
        page.locator("vaadin-dialog[opened]")
                .waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED));

        // Step 3 — diagram container appears (loading completes)
        page.locator("[data-testid='diagram-container']").waitFor();

        // Step 4 — expected task node is rendered with correct text
        Locator taskNode = page.locator("[data-testid='" + taskTestId + "']");
        taskNode.waitFor();
        assertThat(taskNode.textContent())
                .as("task node '%s' must contain text '%s'", taskTestId, taskName)
                .contains(taskName);

        // Step 5 — close and reopen: re-renders correctly (no stale state)
        page.keyboard().press("Escape");
        page.locator("vaadin-dialog[opened]")
                .waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
        page.locator(buttonSelector).click();
        page.locator("[data-testid='diagram-container']").waitFor();

        Locator taskNodeAfterReopen = page.locator("[data-testid='" + taskTestId + "']");
        taskNodeAfterReopen.waitFor();
        assertThat(taskNodeAfterReopen.textContent())
                .as("task node '%s' must still render correctly after reopen", taskTestId)
                .contains(taskName);
    }
}
