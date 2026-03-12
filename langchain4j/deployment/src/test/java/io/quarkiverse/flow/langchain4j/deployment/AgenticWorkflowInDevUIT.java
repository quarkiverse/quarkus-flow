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
import io.quarkus.test.QuarkusDevModeTest;

public class AgenticWorkflowInDevUIT {

    static BrowserType.LaunchOptions launchOptions;

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agents.class));

    @BeforeAll
    static void setUp() {
        launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setChromiumSandbox(false)
                .setChannel("")
                .setArgs(List.of("--disable-gpu"));
    }

    @Test
    void should_show_warning_message_when_looking_for_agentic_mermaid_before_execution() {
        final Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DEBUG", "pw:api");
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(env))) {

            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

                devUI.navigateToDevUIHome();

                devUI.clickOnWorkflows();

                devUI.clickOnEyeButton();

                // opens a modal
                devUI.page().waitForSelector("id=unavailable-mermaid-warning");

                assertThat(devUI.page().isVisible("id=unavailable-mermaid-warning")).isTrue();
            }
        }
    }

    @Test
    void should_call_agentic_workflow() {
        final Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DEBUG", "pw:api");

        SoftAssertions softly = new SoftAssertions();

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(env))) {

            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

                devUI.navigateToDevUIHome();

                softly.assertThat(devUI.workflowsBadgeNumber()).isOne();

                devUI.clickOnWorkflows();

                devUI.clickOnPlayButton();

                devUI.executeWorkflow();

                devUI.waitByAIMessage();

                softly.assertThat(devUI.content()).doesNotContain("# Your workflow output will be displayed here");
            }
        }

        softly.assertAll();
    }

    @Test
    void should_generate_mermaid_diagram_after_first_execution() {
        final Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DEBUG", "pw:api");

        SoftAssertions softly = new SoftAssertions();

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(env))) {

            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                QuarkusDevUIPage devUI = new QuarkusDevUIPage(browser.newPage());

                devUI.navigateToDevUIHome();

                softly.assertThat(devUI.workflowsBadgeNumber()).isOne();

                devUI.clickOnWorkflows();

                devUI.clickOnPlayButton();

                devUI.executeWorkflow();

                devUI.waitByAIMessage();

                softly.assertThat(devUI.content()).doesNotContain("# Your workflow output will be displayed here");

                devUI.navigateToDevUIHome();

                devUI.clickOnWorkflows();

                devUI.clickOnEyeButton();

                devUI.page().waitForCondition(() -> devUI.page().isVisible("pre.mermaid-container"));

                softly.assertThat(devUI.page().isVisible("pre.mermaid-container")).isTrue();
            }
        }

        softly.assertAll();
    }
}
