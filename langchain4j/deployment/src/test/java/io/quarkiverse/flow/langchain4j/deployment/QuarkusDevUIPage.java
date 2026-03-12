package io.quarkiverse.flow.langchain4j.deployment;

import com.microsoft.playwright.Page;

public class QuarkusDevUIPage {

    private final Page page;

    public QuarkusDevUIPage(Page page) {
        this.page = page;
    }

    public void navigateToDevUIHome() {
        page.navigate("http://localhost:8080/q/dev-ui/extensions");
    }

    public Integer workflowsBadgeNumber() {
        return Integer.valueOf(page.locator("[namespace=quarkus-flow] > qui-badge").textContent());
    }

    public void clickOnWorkflows() {
        page.waitForSelector("[href=\"quarkus-flow\\/workflows\"]").click();
    }

    public void clickOnPlayButton() {
        page.waitForSelector(
                "id=play-mermaid-io-quarkiverse-flow-langchain4j-story-creator-with-configurable-style-editor-0-0-1").click();
    }

    public void clickOnEyeButton() {
        page.waitForSelector(
                "id=see-mermaid-io-quarkiverse-flow-langchain4j-story-creator-with-configurable-style-editor-0-0-1").click();
    }

    public void executeWorkflow() {
        page.waitForSelector("id=execute-workflow").click();
    }

    public String content() {
        return page.content();
    }

    public Page page() {
        return this.page;
    }

    public void waitByAIMessage() {
        this.page.waitForCondition(() -> this.page.waitForSelector("[data-language=markdown]").textContent()
                .contains("Your workflow output will be displayed here"));
    }
}
