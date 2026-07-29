# Test: Dev UI Workflow Editor Rendering Round-Trip (quarkus-playwright)

**Labels:** `test`, `dev-ui`, `enhancement`
**Module:** `core/deployment`
**Test type:** Integration Test (IT) — Playwright
**Existing test file:** `WorkflowDiagramEditorIT.java`

---

## Problem Statement

The Dev UI embeds the `@openworkflowspec/diagram-editor` React component inside a Lit Web Component (`qwc-openworkflowspec-diagram-editor`) that is served from an esbuild-compiled bundle. The integration layer between the Quarkus backend and the editor involves several steps:

1. The Java workflow (Java DSL or YAML file) is registered in `WorkflowApplication` at boot.
2. The Dev UI frontend fetches the definition via the `getWorkflowDefinition` JSON-RPC method.
3. The backend serialises the `Workflow` object to JSON (`WorkflowFormat.JSON.mapper().writeValueAsString(workflow)`).
4. The `OpenWorkflowSpecDiagramEditorElement` Lit component receives the JSON string and passes it as the `content` prop to the React `DiagramEditor`.
5. The `DiagramEditor` renders the workflow graph in the browser DOM.

There is currently **no automated test** that validates this full round-trip end-to-end, specifically:

- That the rendered DOM nodes match the workflow's tasks by name and type.
- That the JSON serialised by the backend is *semantically equivalent* to the original workflow definition (no field loss, no mutation introduced by the integration layer).
- That the original `Workflow` Java object is **not mutated** by the serialisation or RPC call (side-effect check).

`WorkflowDiagramEditorIT` already exists and uses raw `com.microsoft.playwright` API directly, but it does not yet cover the full round-trip, does not use the `quarkus-playwright` extension, and does not validate semantic equivalence of the definition.

---

## Proposed Solution

### 1. Extend `WorkflowDiagramEditorIT` with `@QuarkusPlaywright`

Migrate the test class to use the `quarkus-playwright` extension (`io.quarkiverse.playwright:quarkus-playwright`, already present as a test dependency in `core/deployment/pom.xml` at version `${io.quarkiverse.playwright}`). This replaces the manual `Playwright.create()` / `Browser` lifecycle management with the CDI-managed `@InjectPlaywright` and `@InjectPage` mechanism.

### 2. Parametrise over workflow representations

The round-trip should be validated for all supported workflow representations. Use JUnit 5 `@ParameterizedTest` to cover:

- A workflow defined with the **Java DSL** (e.g., `EchoNameWorkflow` — already present).
- A workflow loaded from a **YAML resource file** (e.g., add an `echo-name.yaml` resource to the test archive, similar to what `core/integration-tests` does).

### 3. Round-trip assertions

The test should assert the following sequence:

| Step | Assertion | How |
|------|-----------|-----|
| 1. Page loaded | The workflows grid is visible and the expected workflow row appears. | `Page.waitForSelector` on `#see-{diagramEditorId}` |
| 2. Definition fetched (JSON-RPC) | The JSON returned by `getWorkflowDefinition` parses to a valid `Workflow` object and is semantically equal to the original. | Call `getWorkflowDefinition` via `DevUIJsonRPCTest` in a companion JSON-RPC test; deserialise with `WorkflowFormat.JSON.mapper()`; compare key fields (`name`, `namespace`, `version`, task names). |
| 3. Editor renders | The `[data-testid='diagram-container']` element appears after loading completes. | `Page.waitForSelector` |
| 4. Task nodes rendered | Every task defined in the workflow has a corresponding DOM node with the expected `data-testid` and text content. | Query `[data-testid='set-node-/do/0/{taskName}']` etc. |
| 5. No side-effects | A second call to `getWorkflowDefinition` for the same workflow returns an identical JSON string; calling it N times does not mutate the workflow object in the cache. | Call the RPC method twice and assert string equality; assert `registryCache` size is unchanged (via `getNumbersOfWorkflows` RPC). |

### 4. New test class structure

Place the Playwright-based round-trip test in a new file alongside the existing one:

```
core/deployment/src/test/java/io/quarkiverse/flow/deployment/test/devui/
  WorkflowDiagramEditorRoundTripIT.java   ← new (Playwright round-trip)
  WorkflowDiagramEditorIT.java            ← existing (keep, extend or supersede)
```

Skeleton structure (pseudo-code):

```java
@QuarkusPlaywright   // from io.quarkiverse.playwright
class WorkflowDiagramEditorRoundTripIT {

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(EchoNameWorkflow.class)                          // Java DSL
                    .addAsResource("flow/echo-name.yaml", "flow/echo-name.yaml")); // YAML

    @InjectPage Page page;  // quarkus-playwright injects a ready browser page

    @ParameterizedTest(name = "{0}")
    @MethodSource("workflowCases")
    @DisplayName("editor renders workflow and definition is not mutated")
    void editor_round_trip(String label, String diagramEditorId, String taskTestId, String taskName) {
        // Step 1 — navigate
        page.navigate("http://localhost:8080/q/dev-ui/quarkus-flow/workflows");
        page.waitForSelector("#see-" + diagramEditorId);

        // Step 2 — open dialog
        page.locator("#see-" + diagramEditorId).click();
        page.waitForSelector("vaadin-dialog[opened]");

        // Step 3 — editor rendered
        page.waitForSelector("[data-testid='diagram-container']");

        // Step 4 — task node visible
        Locator taskNode = page.locator("[data-testid='" + taskTestId + "']");
        taskNode.waitFor();
        assertThat(taskNode.textContent()).contains(taskName);

        // Step 5 — no side-effects: close and re-open dialog
        page.keyboard().press("Escape");
        page.locator("#see-" + diagramEditorId).click();
        page.waitForSelector("[data-testid='diagram-container']");
        assertThat(page.locator("[data-testid='" + taskTestId + "']").textContent())
                .contains(taskName);
    }

    static Stream<Arguments> workflowCases() { /* Java-DSL case + YAML case */ }
}
```

The **semantic equivalence** of the serialised JSON (step 2 above) is best validated in a separate, lightweight `DevUIJsonRPCTest` companion so it does not require a full browser:

```java
// companion JSON-RPC test (no Playwright needed)
class WorkflowDefinitionRoundTripJsonRPCTest extends DevUIJsonRPCTest {

    @Test
    @DisplayName("getWorkflowDefinition returns semantically equivalent definition")
    void definition_is_semantically_equivalent() throws Exception {
        WorkflowDefinitionId id = WorkflowDefinitionId.of(new EchoNameWorkflow().descriptor());
        String json1 = executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", id)).asText();
        String json2 = executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", id)).asText();

        // Idempotency: calling twice returns the same result
        assertThat(json1).isEqualTo(json2);

        // Semantic equivalence: round-trip through the SDK parser
        Workflow roundTripped = WorkflowFormat.JSON.mapper().readValue(json1, Workflow.class);
        assertThat(roundTripped.getDocument().getName()).isEqualTo("echo-name");
        assertThat(roundTripped.getDocument().getNamespace()).isEqualTo("flow");
        assertThat(roundTripped.getDocument().getVersion()).isEqualTo("0.1.0");
        // Tasks are preserved
        assertThat(roundTripped.getDo()).hasSize(1);
        assertThat(roundTripped.getDo().get(0)).containsKey("setEcho");
    }

    @Test
    @DisplayName("repeated calls do not increase workflow count (no side-effect on cache)")
    void repeated_calls_do_not_mutate_registry() throws Exception {
        int before = executeJsonRPCMethod("getNumbersOfWorkflows").asInt();
        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", /* id */ ...));
        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", /* id */ ...));
        int after = executeJsonRPCMethod("getNumbersOfWorkflows").asInt();
        assertThat(after).isEqualTo(before);
    }
}
```

---

## Acceptance Criteria

- [ ] Tests live in `core/deployment` test sources, run as ITs (`*IT.java`) via Failsafe with `-DskipITs=false`.
- [ ] Use `@QuarkusPlaywright` / `@InjectPage` from `quarkus-playwright` — no manual Playwright lifecycle.
- [ ] Both Java DSL and YAML-based workflows are covered as parametrised cases.
- [ ] Rendering round-trip is validated: the DOM node matching each workflow task is found with the correct `data-testid` and text.
- [ ] Semantic equivalence is asserted: JSON returned by `getWorkflowDefinition` deserialises back to a `Workflow` with identical `name`, `namespace`, `version`, and task list.
- [ ] Side-effect freedom is asserted: calling the RPC method N times does not mutate the workflow registry or return different content.
- [ ] Tests use AssertJ for assertions and follow `snake_case` + `@DisplayName` naming convention.
- [ ] No fixed ports (random port or Quarkus-assigned port used).

---

## Out of Scope

- Testing the diagram editor for writability / mutations from the UI — the editor is read-only in Dev UI.
- Testing visual pixel accuracy of the diagram rendering.
- Testing the editor outside of Quarkus Dev Mode.

---

## Related

- Existing test: `core/deployment/src/test/java/…/devui/WorkflowDiagramEditorIT.java`
- Existing JSON-RPC test: `FlowWorkflowDefinitionDevUIJsonRPCTest.java`
- Integration layer: `WorkflowRPCService#getWorkflowDefinition` (`core/runtime-dev`)
- Frontend entry: `openworkflowspec-diagram-editor.js` (`core/deployment/src/main/resources/dev-ui`)
- Playwright version property: `${io.quarkiverse.playwright}` = `2.3.7` (defined in root `pom.xml`)

> **Note on the existing `WorkflowDiagramEditorIT`:** The file already exists and validates loading state and node rendering using raw Playwright API. The new suite either extends it to adopt `@QuarkusPlaywright` or adds a parallel class. The decision is left to the implementer — both approaches are valid; the round-trip and side-effect tests are the new value-add.
