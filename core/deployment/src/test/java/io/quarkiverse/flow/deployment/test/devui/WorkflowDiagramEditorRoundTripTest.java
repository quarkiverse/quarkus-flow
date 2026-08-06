package io.quarkiverse.flow.deployment.test.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import org.acme.ConditionalWorkflow;
import org.acme.ContextWorkflow;
import org.acme.CronWorkflow;
import org.acme.EmitWorkflow;
import org.acme.ForEachWorkflow;
import org.acme.HelloWorkflow;
import org.acme.HttpWorkflow;
import org.acme.ListenWorkflow;
import org.acme.Message;
import org.acme.Order;
import org.acme.OrdersPayload;
import org.acme.ParallelWorkflow;
import org.acme.ParentWorkflow;
import org.acme.ScorePayload;
import org.acme.dataflow.Call4PapersFlow;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
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

public class WorkflowDiagramEditorRoundTripTest {

    // Live references to the docs YAML files — not copies. Any breaking change to a file
    // immediately fails the corresponding parametrised case. Paths are relative to the Maven
    // module root (core/deployment/), which is the working directory at test runtime.
    static final File ECHO_NAME_YAML = new File(
            "../../docs/modules/ROOT/examples/flow/echo-name.yaml");
    static final File ECHO_NAME_V2_YAML = new File(
            "../../docs/modules/ROOT/examples/flow/echo-name-v2.yaml");

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            HelloWorkflow.class,
                            ParallelWorkflow.class,
                            ConditionalWorkflow.class,
                            ScorePayload.class,
                            ForEachWorkflow.class,
                            OrdersPayload.class,
                            Order.class,
                            EmitWorkflow.class,
                            Message.class,
                            ListenWorkflow.class,
                            ParentWorkflow.class,
                            HttpWorkflow.class,
                            CronWorkflow.class,
                            ContextWorkflow.class,
                            Call4PapersFlow.class)
                    .addAsResource(new StringAsset(
                            "wiremock.url=http://localhost:9999\n" +
                                    "notification.service.base-url=http://localhost:9999\n" +
                                    "quarkus.flow.definitions.dir=flow\n"),
                            "application.properties")
                    .addAsResource(new FileAsset(ECHO_NAME_YAML), "flow/echo-name.yaml")
                    .addAsResource(new FileAsset(ECHO_NAME_V2_YAML), "flow/echo-name-v2.yaml"));

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
    static Stream<Arguments> workflowCases() {
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
                        "switch-0"),
                // forEach task — auto-named for-0; covers the for-node diagram element.
                Arguments.of(
                        "foreach-java",
                        "diagramEditor-org-acme-foreach-workflow-0-0-1",
                        "for-node-/do/0/for-0",
                        "for-0"),
                // emit task — covers the emit-node diagram element.
                Arguments.of(
                        "emit-java",
                        "diagramEditor-org-acme-emit-event-workflow-1-0",
                        "emit-node-/do/0/orderPlaced",
                        "orderPlaced"),
                // listen task — covers the listen-node diagram element.
                Arguments.of(
                        "listen-java",
                        "diagramEditor-org-acme-listen-to-one-workflow-0-0-1",
                        "listen-node-/do/0/waitForStartup",
                        "waitForStartup"),
                // subflow task — mapped to run-node in the diagram editor.
                Arguments.of(
                        "parent-java",
                        "diagramEditor-org-acme-parent-workflow-with-children-1-0",
                        "run-node-/do/0/executeHttpWorkflow",
                        "executeHttpWorkflow"),
                // standalone named HTTP call — dedicated call-node root-level case.
                // org.acme namespace (dotted) → org-acme in diagramEditorId; version 1.0 → 1-0.
                Arguments.of(
                        "http-call-java",
                        "diagramEditor-org-acme-http-with-query-headers-1-0",
                        "call-node-/do/0/searchStarWarsCharacters",
                        "searchStarWarsCharacters"),
                // cron-scheduled workflow — only case with a schedule block in the serialised JSON.
                Arguments.of(
                        "cron-java",
                        "diagramEditor-org-acme-cron-workflow-0-0-1",
                        "set-node-/do/0/set-0",
                        "set-0"),
                // withContext lambda task — call-node with Java badge (auto-named function-0).
                Arguments.of(
                        "context-java",
                        "diagramEditor-org-acme-context-aware-0-0-1",
                        "call-node-/do/0/function-0",
                        "function-0"),
                // 4-task dataflow workflow: function + inputFrom/outputAs/exportAs + HTTP.
                // Most complex serialisation surface in the docs examples.
                Arguments.of(
                        "call4papers-java",
                        "diagramEditor-org-acme-call4papers-0-0-1",
                        "call-node-/do/0/validateProposal",
                        "validateProposal"),
                // YAML cases — live references to docs/modules/ROOT/examples/flow/*.yaml.
                // namespace=company, name=echo-name, version=0.1.0 → dots/dots become dashes.
                Arguments.of(
                        "echo-yaml",
                        "diagramEditor-company-echo-name-0-1-0",
                        "set-node-/do/0/setEcho",
                        "setEcho"),
                // namespace=flow, name=echo-name, version=0.2.0
                Arguments.of(
                        "echo-yaml-v2",
                        "diagramEditor-flow-echo-name-0-2-0",
                        "set-node-/do/0/setEcho",
                        "setEcho"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("workflowCases")
    @DisplayName("editor renders workflow and re-renders without side-effects")
    void editorRendersWorkflow(
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
