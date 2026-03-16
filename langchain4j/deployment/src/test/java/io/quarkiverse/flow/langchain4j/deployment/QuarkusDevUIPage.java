package io.quarkiverse.flow.langchain4j.deployment;

import com.microsoft.playwright.Page;

public record QuarkusDevUIPage(Page page) {

    private static final String DEV_UI_BASE_URL = "http://localhost:8080/q/dev-ui";
    private static final String WORKFLOWS_LINK_SELECTOR = "[href=\"quarkus-flow\\/workflows\"]";
    private static final String WORKFLOWS_BADGE_SELECTOR = "[namespace=quarkus-flow] > qui-badge";
    private static final String EXECUTE_WORKFLOW_SELECTOR = "id=execute-workflow";
    private static final String MARKDOWN_OUTPUT_SELECTOR = "[data-language=markdown]";
    private static final String DOWNLOAD_DIAGRAM_SELECTOR = "id=download-diagram";
    private static final String MERMAID_CONTAINER_SELECTOR = "pre.mermaid-container";

    public void navigateToDevUIHome() {
        page.navigate(DEV_UI_BASE_URL + "/extensions");
    }

    public Integer workflowsBadgeNumber() {
        page.waitForSelector(WORKFLOWS_BADGE_SELECTOR);
        return Integer.valueOf(page.locator(WORKFLOWS_BADGE_SELECTOR).textContent());
    }

    public void clickOnWorkflows() {
        page.waitForSelector(WORKFLOWS_LINK_SELECTOR).click();
    }

    public void clickOnPlayButton(String buttonId) {
        clickOnButton(buttonId);
    }

    public void clickOnEyeButton(String buttonId) {
        clickOnButton(buttonId);
    }

    public void executeWorkflow() {
        page.waitForSelector(EXECUTE_WORKFLOW_SELECTOR).click();
    }

    public String content() {
        return page.content();
    }

    public void waitByAIMessage() {
        page.waitForCondition(() -> page.waitForSelector(MARKDOWN_OUTPUT_SELECTOR)
                .textContent()
                .contains("Your workflow output will be displayed here"));
    }

    public void clickOnDownload() {
        page.waitForSelector(DOWNLOAD_DIAGRAM_SELECTOR).click();
    }

    public void waitForMermaidContainer() {
        page.waitForCondition(() -> page.isVisible(MERMAID_CONTAINER_SELECTOR));
    }

    public boolean isMermaidContainerVisible() {
        return page.isVisible(MERMAID_CONTAINER_SELECTOR);
    }

    public boolean isDownloadButtonVisible() {
        return page.isVisible(DOWNLOAD_DIAGRAM_SELECTOR);
    }

    public boolean isElementVisible(String selector) {
        return page.isVisible(selector);
    }

    public void waitForElement(String selector) {
        page.waitForSelector(selector);
    }

    private void clickOnButton(String buttonId) {
        page.waitForSelector("id=" + buttonId).click();
    }
}
