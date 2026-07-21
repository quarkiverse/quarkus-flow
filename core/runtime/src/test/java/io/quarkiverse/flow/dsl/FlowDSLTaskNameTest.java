package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static io.quarkiverse.flow.dsl.FlowDSL.forEach;
import static io.quarkiverse.flow.dsl.FlowDSL.forEachItem;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.configurers.FuncTaskConfigurer;
import io.quarkiverse.flow.dsl.types.LoopFunction;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;

@DisplayName("FlowDSL — named control-flow overloads")
class FlowDSLTaskNameTest {

    private static Workflow buildWorkflow(FuncTaskConfigurer... steps) {
        return FlowWorkflowBuilder.workflow("taskNameTest").tasks(steps).build();
    }

    private static List<TaskItem> buildItems(FuncTaskConfigurer... steps) {
        return buildWorkflow(steps).getDo();
    }

    @Nested
    @DisplayName("switchWhen — named overloads")
    class SwitchWhenTest {

        @Test
        @DisplayName("named predicate overload uses taskName and stores predicate case")
        void namedPredicateOverload() {
            var items = buildItems(switchWhen("checkSign", (Integer v) -> v > 0, "positive", Integer.class));
            assertEquals("checkSign", items.get(0).getName());
            SwitchCase sc = items.get(0).getTask().getSwitchTask().getSwitch().get(0).getSwitchCase();
            assertEquals("positive", sc.getThen().getString());
        }

        @Test
        @DisplayName("named JQ overload uses taskName and configures when expression")
        void namedJqOverload() {
            var items = buildItems(switchWhen("approvalGate", ".approved == true", "approveOrder"));
            assertEquals("approvalGate", items.get(0).getName());
            assertEquals(
                    ".approved == true",
                    items.get(0).getTask().getSwitchTask().getSwitch().get(0).getSwitchCase().getWhen());
        }

        @Test
        @DisplayName("unnamed overloads produce auto-generated switch- names")
        void unnamedAutoNames() {
            var items = buildItems(
                    switchWhen((Integer v) -> v > 0, "pos", Integer.class), switchWhen(".ok", "go"));
            assertTrue(items.get(0).getName().startsWith("switch-"));
            assertTrue(items.get(1).getName().startsWith("switch-"));
        }
    }

    @Nested
    @DisplayName("switchWhenOrElse — named overloads")
    class SwitchWhenOrElseTest {

        @Test
        @DisplayName("named Predicate + directive overload")
        void namedPredicateDirective() {
            var items = buildItems(
                    switchWhenOrElse(
                            "scoreGate",
                            (Integer v) -> v >= 80,
                            "pass",
                            FlowDirectiveEnum.END,
                            Integer.class));
            assertEquals("scoreGate", items.get(0).getName());
            var cases = items.get(0).getTask().getSwitchTask().getSwitch();
            assertEquals(2, cases.size());
            assertEquals("pass", cases.get(0).getSwitchCase().getThen().getString());
            assertEquals(
                    FlowDirectiveEnum.END, cases.get(1).getSwitchCase().getThen().getFlowDirectiveEnum());
        }

        @Test
        @DisplayName("named SerializablePredicate + orElseTask overload")
        void namedSerializablePredicateTask() {
            var items = buildItems(switchWhenOrElse("signGate", (Integer v) -> v > 0, "positive", "negative"));
            assertEquals("signGate", items.get(0).getName());
            var cases = items.get(0).getTask().getSwitchTask().getSwitch();
            assertEquals("positive", cases.get(0).getSwitchCase().getThen().getString());
            assertEquals("negative", cases.get(1).getSwitchCase().getThen().getString());
        }

        @Test
        @DisplayName("named JQ + directive overload")
        void namedJqDirective() {
            var items = buildItems(switchWhenOrElse("examGate", ".score >= 80", "pass", FlowDirectiveEnum.END));
            assertEquals("examGate", items.get(0).getName());
            var cases = items.get(0).getTask().getSwitchTask().getSwitch();
            assertEquals(".score >= 80", cases.get(0).getSwitchCase().getWhen());
            assertEquals(
                    FlowDirectiveEnum.END, cases.get(1).getSwitchCase().getThen().getFlowDirectiveEnum());
        }

        @Test
        @DisplayName("named JQ + orElseTask overload")
        void namedJqTask() {
            var items = buildItems(switchWhenOrElse("approvalGate", ".approved", "send", "draft"));
            assertEquals("approvalGate", items.get(0).getName());
            var cases = items.get(0).getTask().getSwitchTask().getSwitch();
            assertEquals(".approved", cases.get(0).getSwitchCase().getWhen());
            assertEquals("draft", cases.get(1).getSwitchCase().getThen().getString());
        }

        @Test
        @DisplayName("unnamed overloads produce auto-generated switch- names")
        void unnamedAutoNames() {
            var items = buildItems(
                    switchWhenOrElse((Integer v) -> v > 0, "pos", FlowDirectiveEnum.END, Integer.class),
                    switchWhenOrElse((Integer v) -> v > 0, "pos", "neg"),
                    switchWhenOrElse(".ok", "go", FlowDirectiveEnum.END),
                    switchWhenOrElse(".ok", "go", "nope"));
            for (int i = 0; i < items.size(); i++) {
                assertTrue(items.get(i).getName().startsWith("switch-"), "item " + i);
            }
        }

        @Test
        @DisplayName("JQ overloads throw NPE on null args")
        void jqNullArgValidation() {
            assertThrows(
                    NullPointerException.class,
                    () -> switchWhenOrElse((String) null, "pass", FlowDirectiveEnum.END));
            assertThrows(
                    NullPointerException.class,
                    () -> switchWhenOrElse("gate", (String) null, "pass", FlowDirectiveEnum.END));
            assertThrows(
                    NullPointerException.class,
                    () -> switchWhenOrElse("gate", ".x", (String) null, FlowDirectiveEnum.END));
            assertThrows(
                    NullPointerException.class,
                    () -> switchWhenOrElse("gate", ".x", "pass", (FlowDirectiveEnum) null));
            assertThrows(
                    NullPointerException.class, () -> switchWhenOrElse((String) null, "send", "draft"));
            assertThrows(
                    NullPointerException.class,
                    () -> switchWhenOrElse("gate", (String) null, "send", "draft"));
            assertThrows(
                    NullPointerException.class, () -> switchWhenOrElse("gate", ".x", (String) null, "draft"));
            assertThrows(
                    NullPointerException.class, () -> switchWhenOrElse("gate", ".x", "send", (String) null));
        }
    }

    @Nested
    @DisplayName("forEach / forEachItem — named overloads")
    class ForEachTest {

        @Test
        @DisplayName("named SerializableFunction + body overload")
        void namedFunctionBody() {
            var items = buildItems(forEach("splitItems", (String s) -> List.of(s.split(",")), tb -> {
            }));
            assertEquals("splitItems", items.get(0).getName());
            assertNotNull(items.get(0).getTask().getForTask());
        }

        @Test
        @DisplayName("named SerializableFunction + LoopFunction overload")
        void namedFunctionLoop() {
            LoopFunction<String, String, Object> loopFn = (ctx, item) -> ctx;
            var items = buildItems(forEach("mapItems", (String s) -> List.of(s.split(",")), loopFn));
            assertEquals("mapItems", items.get(0).getName());
        }

        @Test
        @DisplayName("named forEachItem overload")
        void namedForEachItem() {
            var items = buildItems(
                    forEachItem(
                            "transformEach", (String s) -> List.of(s.split(",")), (String item) -> item));
            assertEquals("transformEach", items.get(0).getName());
        }

        @Test
        @DisplayName("named Collection + body overload")
        void namedCollectionBody() {
            var items = buildItems(forEach("iterateItems", List.of("a", "b"), tb -> {
            }));
            assertEquals("iterateItems", items.get(0).getName());
        }

        @Test
        @DisplayName("unnamed overloads produce auto-generated for- names")
        void unnamedAutoNames() {
            LoopFunction<String, String, Object> loopFn = (ctx, item) -> ctx;
            var items = buildItems(
                    forEach((String s) -> List.of(s), tb -> {
                    }),
                    forEach((String s) -> List.of(s), loopFn),
                    forEachItem((String s) -> List.of(s), (String x) -> x),
                    forEach(List.of("a"), tb -> {
                    }),
                    forEach(List.of("x", "y"), tb -> {
                    }));
            for (int i = 0; i < items.size(); i++) {
                assertTrue(items.get(i).getName().startsWith("for-"), "item " + i);
            }
        }
    }

    @Nested
    @DisplayName("Integration — named control flow in realistic workflows")
    class IntegrationTest {

        @Test
        @DisplayName("named switchWhenOrElse with named branches")
        void namedSwitchWithNamedBranches() {
            Workflow wf = buildWorkflow(
                    function("loadData", (String s) -> s, String.class),
                    switchWhenOrElse(
                            "validateData",
                            (String s) -> !s.isEmpty(),
                            "processValid",
                            "handleInvalid",
                            String.class),
                    consume("processValid", (String s) -> {
                    }, String.class),
                    consume("handleInvalid", (String s) -> {
                    }, String.class));
            var items = wf.getDo();
            assertEquals("loadData", items.get(0).getName());
            assertEquals("validateData", items.get(1).getName());
            assertEquals("processValid", items.get(2).getName());
            assertEquals("handleInvalid", items.get(3).getName());
        }

        @Test
        @DisplayName("mixed named and unnamed control flow tasks")
        void mixedNamedAndUnnamed() {
            var items = buildItems(
                    switchWhen("firstGate", (Integer v) -> v > 0, "pos", Integer.class),
                    switchWhen((String s) -> s.isEmpty(), "empty", String.class),
                    forEach("namedLoop", List.of(1, 2), tb -> {
                    }),
                    forEach(List.of("x"), tb -> {
                    }));
            assertEquals("firstGate", items.get(0).getName());
            assertTrue(items.get(1).getName().startsWith("switch-"));
            assertEquals("namedLoop", items.get(2).getName());
            assertTrue(items.get(3).getName().startsWith("for-"));
        }

        @Test
        @DisplayName("named JQ switchWhenOrElse in workflow")
        void namedJqSwitchIntegration() {
            var items = buildItems(
                    function("fetchOrder", (String s) -> s, String.class),
                    switchWhenOrElse("checkApproval", ".approved == true", "fulfillOrder", "rejectOrder"),
                    consume("fulfillOrder", (String s) -> {
                    }, String.class),
                    consume("rejectOrder", (String s) -> {
                    }, String.class));
            assertEquals("checkApproval", items.get(1).getName());
            var cases = items.get(1).getTask().getSwitchTask().getSwitch();
            assertEquals(2, cases.size());
            assertEquals(".approved == true", cases.get(0).getSwitchCase().getWhen());
            assertEquals("rejectOrder", cases.get(1).getSwitchCase().getThen().getString());
        }
    }
}
