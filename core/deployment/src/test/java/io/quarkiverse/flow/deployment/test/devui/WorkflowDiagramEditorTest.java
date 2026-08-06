package io.quarkiverse.flow.deployment.test.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import io.quarkus.test.QuarkusDevModeTest;

public class WorkflowDiagramEditorTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(EchoNameWorkflow.class));

    static final String DIAGRAM_EDITOR_ID = "diagramEditor-flow-echo-name-0-1-0";
    static final String BUTTON_SELECTOR = "#see-" + DIAGRAM_EDITOR_ID;
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

    @BeforeEach
    void openPage() {
        page = browserContext.newPage();
    }

    @AfterEach
    void closePage() {
        page.close();
    }

    @Test
    @DisplayName("loading state is shown before DiagramEditor renders")
    void shouldShowLoadingBeforeDiagramEditor() {
        page.navigate(DEV_UI_URL);
        page.waitForSelector(BUTTON_SELECTOR);
        page.locator(BUTTON_SELECTOR).click();

        ElementHandle dialog = page.waitForSelector("vaadin-dialog[opened]",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));

        String initialHtml = dialog.innerHTML();
        boolean hasLoadingState = initialHtml.contains("Loading workflow definition") ||
                dialog.querySelector("vaadin-progress-bar[indeterminate]") != null;

        assertThat(hasLoadingState)
                .as("loading state (progress bar or text) must be visible immediately after dialog opens")
                .isTrue();

        assertThat(dialog.querySelector("[data-testid='diagram-container']"))
                .as("DiagramEditor must NOT be visible during loading state")
                .isNull();

        page.waitForSelector("[data-testid='diagram-container']");

        assertThat(dialog.querySelector("vaadin-progress-bar[indeterminate]"))
                .as("progress bar must be removed after loading completes")
                .isNull();
    }

    @Test
    @DisplayName("DiagramEditor renders the setEcho task node")
    void shouldRenderDiagramEditorWithSetEchoNode() {
        page.navigate(DEV_UI_URL);
        page.waitForSelector(BUTTON_SELECTOR);
        page.locator(BUTTON_SELECTOR).click();

        page.waitForSelector("vaadin-dialog[opened]",
                new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));

        page.waitForSelector("[data-testid='diagram-container']");

        String nodeSelector = "[data-testid='set-node-/do/0/setEcho']";
        page.waitForSelector(nodeSelector);
        Locator setEchoNode = page.locator(nodeSelector);

        assertThat(setEchoNode.count())
                .as("DiagramEditor must render the setEcho node with data-testid='set-node-/do/0/setEcho'")
                .isGreaterThan(0);

        assertThat(setEchoNode.first().textContent())
                .as("setEcho node must contain the text 'setEcho'")
                .contains("setEcho");
    }
}
