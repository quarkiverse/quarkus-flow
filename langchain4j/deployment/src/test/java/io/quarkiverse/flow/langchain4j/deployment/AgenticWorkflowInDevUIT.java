package io.quarkiverse.flow.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import io.quarkiverse.flow.langchain4j.Agents;
import io.quarkiverse.flow.langchain4j.SimpleWorkflow;
import io.quarkus.test.QuarkusDevModeTest;

public class AgenticWorkflowInDevUIT {

    private static final String PLAY_AGENTIC_ID = "play-mermaid-io-quarkiverse-flow-langchain4j-story-creator-with-configurable-style-editor-0-0-1";
    private static final String SEE_AGENTIC_ID = "see-mermaid-io-quarkiverse-flow-langchain4j-story-creator-with-configurable-style-editor-0-0-1";
    private static final String SEE_SIMPLE_ID = "see-mermaid-quarkus-flow-simple-workflow-0-0-1";
    private static final String UNAVAILABLE_MERMAID_WARNING_SELECTOR = "id=unavailable-mermaid-warning";

    private static BrowserType.LaunchOptions launchOptions;

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agents.class, SimpleWorkflow.class));

    @BeforeAll
    static void setUp() {
        launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChromiumSandbox(false)
                .setChannel("")
                .setArgs(List.of("--disable-gpu"));
    }

    @Test
    void should_show_warning_message_when_looking_for_agentic_mermaid_before_execution() {
        try (Playwright playwright = createPlaywright();
                Browser browser = playwright.chromium().launch(launchOptions)) {

            QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

            devUI.navigateToDevUIHome();
            devUI.clickOnWorkflows();
            devUI.clickOnEyeButton(SEE_AGENTIC_ID);

            devUI.waitForElement(UNAVAILABLE_MERMAID_WARNING_SELECTOR);

            assertThat(devUI.isElementVisible(UNAVAILABLE_MERMAID_WARNING_SELECTOR))
                    .as("Warning message should be visible when mermaid diagram is not available")
                    .isTrue();
        }
    }

    @Test
    void should_call_agentic_workflow() {
        SoftAssertions softly = new SoftAssertions();

        try (Playwright playwright = createPlaywright();
                Browser browser = playwright.chromium().launch(launchOptions)) {

            QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

            devUI.navigateToDevUIHome();

            softly.assertThat(devUI.workflowsBadgeNumber())
                    .as("Workflows badge should show available workflows")
                    .isGreaterThanOrEqualTo(1);

            devUI.clickOnWorkflows();
            devUI.clickOnPlayButton(PLAY_AGENTIC_ID);
            devUI.executeWorkflow();
            devUI.waitByAIMessage();

            softly.assertThat(devUI.content())
                    .as("Workflow output should be displayed")
                    .doesNotContain("# Your workflow output will be displayed here");
        }

        softly.assertAll();
    }

    @Test
    void should_generate_mermaid_diagram_after_first_execution() {
        SoftAssertions softly = new SoftAssertions();

        try (Playwright playwright = createPlaywright();
                Browser browser = playwright.chromium().launch(launchOptions)) {

            QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

            devUI.navigateToDevUIHome();

            softly.assertThat(devUI.workflowsBadgeNumber())
                    .as("Workflows badge should show available workflows")
                    .isGreaterThanOrEqualTo(1);

            devUI.clickOnWorkflows();
            devUI.clickOnPlayButton(PLAY_AGENTIC_ID);
            devUI.executeWorkflow();
            devUI.waitByAIMessage();

            softly.assertThat(devUI.content())
                    .as("Workflow output should be displayed")
                    .doesNotContain("# Your workflow output will be displayed here");

            devUI.navigateToDevUIHome();
            devUI.clickOnWorkflows();
            devUI.clickOnEyeButton(SEE_AGENTIC_ID);

            devUI.waitForMermaidContainer();

            softly.assertThat(devUI.isMermaidContainerVisible())
                    .as("Mermaid diagram should be visible after workflow execution")
                    .isTrue();
        }

        softly.assertAll();
    }

    @Test
    void should_download_the_generated_mermaid_diagram() {
        SoftAssertions softly = new SoftAssertions();

        try (Playwright playwright = createPlaywright();
                Browser browser = playwright.chromium().launch(launchOptions)) {

            QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

            devUI.navigateToDevUIHome();
            devUI.clickOnWorkflows();
            devUI.clickOnEyeButton(SEE_SIMPLE_ID);

            devUI.waitForMermaidContainer();

            softly.assertThat(devUI.isMermaidContainerVisible())
                    .as("Mermaid diagram should be visible before download")
                    .isTrue();

            softly.assertThat(devUI.isDownloadButtonVisible())
                    .as("Download button should be visible")
                    .isTrue();

            devUI.clickOnDownload();

            softly.assertThat(devUI.isDownloadButtonVisible())
                    .as("Download button should still be visible after download")
                    .isTrue();
        }

        softly.assertAll();
    }

    private static Playwright createPlaywright() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DEBUG", "pw:api");
        return Playwright.create(new Playwright.CreateOptions().setEnv(env));
    }
}
