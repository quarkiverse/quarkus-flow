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

import io.quarkiverse.flow.deployment.test.HelloWorldWorkflow;
import io.quarkus.test.QuarkusDevModeTest;

public class WorkflowMermaidIT {

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(HelloWorldWorkflow.class));

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
    @DisplayName("Should generate the diagram as expected")
    void shouldGenerateMermaidSvg() {
        final Map<String, String> env = new HashMap<>(System.getenv());
        //        env.put("DEBUG", "pw:api");

        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {

            try (Browser browser = playwright.chromium().launch(launchOptions)) {

                Page page = browser.newPage();
                page.navigate("http://localhost:8080/q/dev-ui/quarkus-flow/workflows");

                String mermaidId = "mermaid-devui-hello-world-v1";
                String buttonSelector = "#see-" + mermaidId;

                page.waitForSelector(buttonSelector);
                Locator eyeButton = page.locator(buttonSelector);

                eyeButton.click();

                ElementHandle dialog = page.waitForSelector("vaadin-dialog[opened]", new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.ATTACHED));

                // Wait for the dialog to open and mermaid rendering
                page.waitForTimeout(8000);

                Assertions.assertTrue(dialog.innerText().contains("set: setMessage")); // the name of the set task
            }
        }
    }
}
