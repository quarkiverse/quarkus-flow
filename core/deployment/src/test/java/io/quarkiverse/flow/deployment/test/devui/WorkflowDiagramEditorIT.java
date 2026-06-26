package io.quarkiverse.flow.deployment.test.devui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import io.quarkus.test.QuarkusDevModeTest;

public class WorkflowDiagramEditorIT {

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(EchoNameWorkflow.class));

    static BrowserType.LaunchOptions launchOptions;

    @BeforeAll
    static void setUp() {
        launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChromiumSandbox(false)
                .setChannel("")
                .setArgs(List.of("--disable-gpu"));
    }

    @Test
    @DisplayName("Should show loading state before DiagramEditor renders")
    void shouldShowLoadingBeforeDiagramEditor() {
        final Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DEBUG", "pw:api");

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                Page page = browser.newPage();
                page.navigate("http://localhost:8080/q/dev-ui/quarkus-flow/workflows");

                String diagramEditorId = "diagramEditor-flow-echo-name-0-1-0";
                String buttonSelector = "#see-" + diagramEditorId;

                page.waitForSelector(buttonSelector);
                Locator eyeButton = page.locator(buttonSelector);

                eyeButton.click();

                ElementHandle dialog = page.waitForSelector("vaadin-dialog[opened]", new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.ATTACHED));

                String initialHtml = dialog.innerHTML();
                boolean hasLoadingState = initialHtml.contains("Loading workflow definition") ||
                        dialog.querySelector("vaadin-progress-bar[indeterminate]") != null;

                Assertions.assertTrue(hasLoadingState,
                        "Loading state (progress bar or text) must be visible immediately after dialog opens");

                ElementHandle editorBeforeLoad = dialog.querySelector("[data-testid='diagram-container']");
                Assertions.assertNull(editorBeforeLoad,
                        "DiagramEditor should NOT be visible during loading state");

                page.waitForSelector("[data-testid='diagram-container']");

                ElementHandle progressBarAfter = dialog.querySelector("vaadin-progress-bar[indeterminate]");
                Assertions.assertNull(progressBarAfter,
                        "Progress bar should be removed after loading completes");
            }
        }
    }

    @Test
    @DisplayName("Should render DiagramEditor with greet node")
    void shouldRenderDiagramEditorWithGreetNode() {
        final Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DEBUG", "pw:api");

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                Page page = browser.newPage();
                page.navigate("http://localhost:8080/q/dev-ui/quarkus-flow/workflows");

                String diagramEditorId = "diagramEditor-flow-echo-name-0-1-0";
                String buttonSelector = "#see-" + diagramEditorId;

                page.waitForSelector(buttonSelector);
                Locator eyeButton = page.locator(buttonSelector);

                eyeButton.click();

                ElementHandle dialog = page.waitForSelector("vaadin-dialog[opened]", new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.ATTACHED));

                page.waitForSelector("[data-testid='diagram-container']");

                String nodeSelector = "[data-testid='set-node-/do/0/setEcho']";
                page.waitForSelector(nodeSelector);
                ElementHandle setEchoNode = dialog.querySelector(nodeSelector);

                Assertions.assertNotNull(setEchoNode,
                        "DiagramEditor should render the setEcho node with data-testid='set-node-/do/0/setEcho'");

                String nodeText = setEchoNode.textContent();
                Assertions.assertTrue(nodeText.contains("setEcho"),
                        "setEcho node should contain the text 'setEcho'");
            }
        }
    }
}
