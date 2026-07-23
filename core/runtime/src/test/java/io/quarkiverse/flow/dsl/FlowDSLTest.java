package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.call;
import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static io.quarkiverse.flow.dsl.FlowDSL.emit;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.get;
import static io.quarkiverse.flow.dsl.FlowDSL.grpc;
import static io.quarkiverse.flow.dsl.FlowDSL.http;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.produced;
import static io.quarkiverse.flow.dsl.FlowDSL.producedJson;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.use;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.Export;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.RunTask;
import io.serverlessworkflow.api.types.RunWorkflow;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;

/** Tests for Step chaining (exportAs/when) over function/emit/listen. */
class FlowDSLTest {

    @Test
    void function_step_exportAs_function_sets_export() {
        Workflow wf = FlowWorkflowBuilder.workflow("step-function-export")
                .tasks(
                        // call + chain exportAs
                        function(String::trim, String.class)
                                .exportAs((String s) -> Map.of("len", s.length())))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");
        Export ex = ((CallFunction) t.getCallTask().get()).getExport();
        assertNotNull(ex, "Export should be set via Step.exportAs(Function)");
        assertNotNull(ex.getAs(), "'as' should be populated");
        // functional export should not produce a literal string
        assertNull(
                ex.getAs().getString(), "Export 'as' must not be a literal string when using Function");
    }

    @Test
    void function_step_when_compiles_and_builds() {
        Workflow wf = FlowWorkflowBuilder.workflow("step-function-when")
                .tasks(
                        function((Integer v) -> v + 1, Integer.class)
                                .when((Integer v) -> v > 0, Integer.class))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());
        assertNotNull(items.get(0).getTask().getCallTask(), "CallTask should still be present");
        // We don't assert internal predicate storage details; just ensure build success & presence.
    }

    @Test
    void emit_step_exportAs_javaFilter_sets_export() {
        FilterFunction<String, Map<String, Object>> jf = (val, wfCtx, taskCtx) -> Map.of("wrapped", val, "wfId",
                wfCtx.instanceData().id());

        Workflow wf = FlowWorkflowBuilder.workflow("step-emit-export")
                .tasks(
                        emit("emitWrapped", produced("org.acme.signal").bytesDataUtf8())
                                .exportAs(jf)) // chaining on Step
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());
        Task t = items.get(0).getTask();
        assertNotNull(t.getEmitTask(), "EmitTask expected");

        // Export is attached to Task
        Export ex = t.getEmitTask().getExport();
        assertNotNull(ex, "Export should be set via Step.exportAs(JavaFilterFunction)");
        assertNotNull(ex.getAs(), "'as' should be populated");
        assertNull(
                ex.getAs().getString(), "Export 'as' must not be a literal string when using function");
    }

    @Test
    @DisplayName("listen(spec).exportAs(Function) sets Export on ListenTask holder")
    void listen_step_exportAs_function_sets_export() {
        Workflow wf = FlowWorkflowBuilder.workflow("step-listen-export")
                .tasks(
                        listen("waitHumanReview", toOne("org.acme.review.done"))
                                .exportAs((Object e) -> Map.of("seen", true)))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());
        Task t = items.get(0).getTask();
        assertNotNull(t.getListenTask(), "ListenTask expected");

        Export ex = t.getListenTask().getExport();
        assertNotNull(ex, "Export should be set via Step.exportAs(Function)");
        assertNotNull(ex.getAs(), "'as' should be populated");
        assertNull(
                ex.getAs().getString(), "Export 'as' must not be a literal string when using function");
    }

    @Test
    @DisplayName("emit helpers default the CloudEvent source to quarkus-flow:local")
    void emit_helpers_default_source() {
        Workflow wf = FlowWorkflowBuilder.workflow("emit-default-source")
                .tasks(emit(producedJson("org.acme.created", String.class)))
                .build();

        EmitTask et = wf.getDo().get(0).getTask().getEmitTask();
        assertNotNull(et, "EmitTask expected");
        assertEquals(
                FlowDSL.DEFAULT_EMIT_SOURCE,
                et.getEmit().getEvent().getWith().getSource().getUriTemplate().getLiteralUri().toString());
    }

    @Test
    @DisplayName("an explicit source overrides the default")
    void emit_explicit_source_overrides_default() {
        Workflow wf = FlowWorkflowBuilder.workflow("emit-explicit-source")
                .tasks(emit(produced("org.acme.created").source("https://acme.org/orders").bytesDataUtf8()))
                .build();

        EmitTask et = wf.getDo().get(0).getTask().getEmitTask();
        assertNotNull(et, "EmitTask expected");
        assertEquals(
                "https://acme.org/orders",
                et.getEmit().getEvent().getWith().getSource().getUriTemplate().getLiteralUri().toString());
    }

    @Test
    @DisplayName("emit(produced(type, fn)).when(...) -> still an EmitTask and builds")
    void emit_step_when_compiles_and_builds() {
        Workflow wf = FlowWorkflowBuilder.workflow("step-emit-when")
                .tasks(emit(produced("org.acme.sig").bytesDataUtf8()).when((Object ctx) -> true))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());
        assertNotNull(items.get(0).getTask().getEmitTask(), "EmitTask should still be present");
    }

    @Test
    @DisplayName("Mixed chaining: function.exportAs -> emit.when -> listen.exportAs")
    void mixed_chaining_order_and_exports() {
        Workflow wf = FlowWorkflowBuilder.workflow("step-mixed")
                .tasks(
                        function(String::strip, String.class).exportAs((String s) -> Map.of("s", s)),
                        emit(produced("org.acme.kickoff").bytesDataUtf8()).when((Object ignore) -> true),
                        listen(toOne("org.acme.done")).exportAs((Object e) -> Map.of("ok", true)))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(3, items.size());

        Task t0 = items.get(0).getTask();
        Task t1 = items.get(1).getTask();
        Task t2 = items.get(2).getTask();

        assertNotNull(t0.getCallTask());
        assertNotNull(t1.getEmitTask());
        assertNotNull(t2.getListenTask());

        assertNotNull(
                ((CallFunction) t0.getCallTask().get()).getExport(), "function step should carry export");
        assertNotNull(t2.getListenTask().getExport(), "listen step should carry export");
    }

    @Test
    void switchWhenOrElse_jq_to_taskName() {
        Workflow wf = FlowWorkflowBuilder.workflow("jqSwitch")
                .tasks(switchWhenOrElse(".approved", "send", "draft"))
                .build();
        Task switchTask = wf.getDo().get(0).getTask();
        assertNotNull(switchTask.getSwitchTask());
        var items = switchTask.getSwitchTask().getSwitch();
        assertEquals(2, items.size());
        assertEquals(".approved", items.get(0).getSwitchCase().getWhen());
    }

    @Test
    void switchWhenOrElse_jq_to_directive() {
        Workflow wf = FlowWorkflowBuilder.workflow("jqSwitchDir")
                .tasks(switchWhenOrElse(".score >= 80", "pass", FlowDirectiveEnum.END))
                .build();
        Task switchTask = wf.getDo().get(0).getTask();
        var items = switchTask.getSwitchTask().getSwitch();
        assertEquals(".score >= 80", items.get(0).getSwitchCase().getWhen());
        assertEquals(
                FlowDirectiveEnum.END, items.get(1).getSwitchCase().getThen().getFlowDirectiveEnum());
    }

    @Test
    void http_spec_via_call_builds_call_http_task() {
        Workflow wf = FlowWorkflowBuilder.workflow("http-call-spec")
                .tasks(call("checkHealth", http().get().endpoint("http://service/health")))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected for HTTP call");

        // HTTP-specific call
        assertInstanceOf(
                CallHTTP.class, t.getCallTask().get(), "CallTask should be an instance of CallHTTP");

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("GET", http.getWith().getMethod(), "HTTP method should be GET");
        assertEquals(
                "http://service/health",
                http.getWith().getEndpoint().getUriTemplate().getLiteralUri().toString(),
                "endpoint should match the DSL endpoint");
    }

    @Test
    @DisplayName("get(endpoint) convenience creates unnamed GET CallHTTP task")
    void get_convenience_creates_http_get() {
        Workflow wf = FlowWorkflowBuilder.workflow("http-get-convenience")
                .tasks(get("http://service/status"))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("GET", http.getWith().getMethod());
        assertEquals(
                "http://service/status",
                http.getWith().getEndpoint().getUriTemplate().getLiteralUri().toString(),
                "endpoint should be set from get(endpoint)");
    }

    @Test
    @DisplayName("get(name, endpoint, auth -> auth.use(\"auth-id\")) wires authentication")
    void get_named_with_authentication_uses_auth_policy() {
        Workflow wf = FlowWorkflowBuilder.workflow("http-get-auth")
                .tasks(get("fetchUsers", "http://service/api/users", use("user-service-auth")))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        assertEquals("fetchUsers", items.get(0).getName(), "Task should use the provided name");
        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("GET", http.getWith().getMethod());
        assertEquals(
                "http://service/api/users",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getUri()
                        .getLiteralEndpointURI()
                        .getLiteralUri()
                        .toString(),
                "endpoint should be set from get(name, endpoint, auth)");

        assertNotNull(
                http.getWith().getEndpoint().getEndpointConfiguration().getAuthentication(),
                "authentication should be configured");
        assertEquals(
                "user-service-auth",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getAuthentication()
                        .getAuthenticationPolicyReference()
                        .getUse(),
                "auth.use(\"...\") should set authentication.use");
    }

    @Test
    @DisplayName("get(URI endpoint, auth) uses URI and authentication")
    void get_with_uri_and_authentication() {
        URI endpoint = URI.create("https://service.example.com/api/health");

        Workflow wf = FlowWorkflowBuilder.workflow("http-get-uri-auth")
                .tasks(get("checkHealth", endpoint, auth -> auth.use("tls-auth")))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertEquals("checkHealth", items.get(0).getName(), "Task should use the provided name");
        assertNotNull(t.getCallTask());

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("GET", http.getWith().getMethod());
        assertEquals(
                endpoint.toString(),
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getUri()
                        .getLiteralEndpointURI()
                        .getLiteralUri()
                        .toString(),
                "endpoint should be derived from URI");

        assertNotNull(http.getWith().getEndpoint().getEndpointConfiguration().getAuthentication());
        assertEquals(
                "tls-auth",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getAuthentication()
                        .getAuthenticationPolicyReference()
                        .getUse());
    }

    @Test
    @DisplayName("post(body, endpoint) convenience creates POST CallHTTP with body")
    void post_convenience_creates_http_post_with_body() {
        Map<String, Object> body = Map.of("name", "Ricardo");

        Workflow wf = FlowWorkflowBuilder.workflow("http-post-convenience")
                .tasks(FlowDSL.post(body, "http://service/api/users"))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("POST", http.getWith().getMethod());
        assertEquals(
                "http://service/api/users",
                http.getWith().getEndpoint().getUriTemplate().getLiteralUri().toString(),
                "endpoint should be set from post(body, endpoint)");

        assertNotNull(http.getWith().getBody(), "Body should be set on POST");
        assertEquals(body, http.getWith().getBody(), "Body should match the provided payload");
    }

    @Test
    @DisplayName("post(name, body, endpoint, auth) wires name, method, endpoint, body and auth")
    void post_named_with_authentication() {
        Map<String, Object> body = Map.of("id", 123, "status", "NEW");

        Workflow wf = FlowWorkflowBuilder.workflow("http-post-named-auth")
                .tasks(
                        FlowDSL.post(
                                "createOrder",
                                body,
                                "https://orders.example.com/api/orders",
                                auth -> auth.use("orders-auth")))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertEquals("createOrder", items.get(0).getName(), "Task should use the provided name");
        assertNotNull(t.getCallTask());

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("POST", http.getWith().getMethod());
        assertEquals(
                "https://orders.example.com/api/orders",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getUri()
                        .getLiteralEndpointURI()
                        .getLiteralUri()
                        .toString());
        assertEquals(body, http.getWith().getBody());

        assertNotNull(http.getWith().getEndpoint().getEndpointConfiguration().getAuthentication());
        assertEquals(
                "orders-auth",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getAuthentication()
                        .getAuthenticationPolicyReference()
                        .getUse());
    }

    @Test
    @DisplayName("call(http(\"...\", auth)) reuses fluent HTTP spec in call(...)")
    void call_with_preconfigured_http_spec() {
        Workflow wf = FlowWorkflowBuilder.workflow("http-call-preconfigured")
                .tasks(
                        call(
                                "preconfigured",
                                http("http://service/api", auth -> auth.use("svc-auth"))
                                        .post()
                                        .body(Map.of("foo", "bar"))))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertEquals("preconfigured", items.get(0).getName());
        assertNotNull(t.getCallTask());

        CallHTTP http = (CallHTTP) t.getCallTask().get();
        assertEquals("POST", http.getWith().getMethod());
        assertEquals(
                "http://service/api",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getUri()
                        .getLiteralEndpointURI()
                        .getLiteralUri()
                        .toString());
        assertEquals(
                "svc-auth",
                http.getWith()
                        .getEndpoint()
                        .getEndpointConfiguration()
                        .getAuthentication()
                        .getAuthenticationPolicyReference()
                        .getUse());
        assertEquals(Map.of("foo", "bar"), http.getWith().getBody());
    }

    @Test
    @DisplayName("function(name, fn).then(taskName) sets FlowDirective string on the task")
    void function_step_then_task_name_sets_flow_directive() {
        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        function("myfunction", String::trim, String.class).then("otherTask"),
                        function("otherTask", String::strip, String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(2, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallFunction callJava = (CallFunction) t.getCallTask().get();
        assertNotNull(callJava.getThen(), "then() should be set on the task");
        assertEquals("otherTask", callJava.getThen().getString(), "then() should point to 'otherTask'");
    }

    @Test
    @DisplayName("function(name, fn).then(FlowDirectiveEnum.END) sets END directive on the task")
    void function_step_then_flow_directive_enum_sets_end() {
        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(function("myfunction", String::trim, String.class).then(FlowDirectiveEnum.END))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected");

        CallFunction callJava = (CallFunction) t.getCallTask().get();
        assertNotNull(callJava.getThen(), "then() should be set on the task");
        assertEquals(
                FlowDirectiveEnum.END,
                callJava.getThen().getFlowDirectiveEnum(),
                "then() should be FlowDirectiveEnum.END");
    }

    @Test
    @DisplayName("consume(name, Consumer, Class).then(taskName) sets FlowDirective string on the task")
    void consume_step_then_task_name_sets_flow_directive() {
        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        consume("sendNewsletter", (String s) -> {
                        }, String.class).then("otherTask"),
                        function("nextTask", String::strip, String.class),
                        function("otherTask", String::strip, String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(3, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected for consume step");

        CallFunction callJava = (CallFunction) t.getCallTask().get();
        assertNotNull(callJava.getThen(), "then() should be set on the consume task");
        assertEquals("otherTask", callJava.getThen().getString(), "then() should point to 'otherTask'");
    }

    @Test
    @DisplayName("consume(name, Consumer, Class).then(FlowDirectiveEnum.END) sets END directive on the task")
    void consume_step_then_flow_directive_enum_sets_end() {
        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        consume("sendNewsletter", (String s) -> {
                        }, String.class)
                                .then(FlowDirectiveEnum.END))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected for consume step");

        CallFunction callJava = (CallFunction) t.getCallTask().get();
        assertNotNull(callJava.getThen(), "then() should be set on the consume task");
        assertEquals(
                FlowDirectiveEnum.END,
                callJava.getThen().getFlowDirectiveEnum(),
                "then() should be FlowDirectiveEnum.END");
    }

    @Test
    @DisplayName("subflow(workflow(ns,name,ver).input(...).await(...)) creates RunTask/RunWorkflow with expected values")
    void subflow_workflow_spec_with_input_and_await_creates_correct_run_task() {
        Workflow wf = FlowWorkflowBuilder.workflow("parent-workflow")
                .tasks(
                        FlowDSL.subflow(
                                FlowDSL.workflow("org.test", "my-subflow", "1.0.0")
                                        .input("id", 99)
                                        .await(false)))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        TaskItem taskItem = items.get(0);
        Task t = taskItem.getTask();
        assertNotNull(t.getRunTask(), "RunTask expected for subflow");

        RunTask runTask = t.getRunTask();
        assertNotNull(runTask.getRun(), "RunTask configuration should be present");

        RunWorkflow runWorkflow = runTask.getRun().getRunWorkflow();
        assertNotNull(runWorkflow, "RunWorkflow should be selected");

        // Verify workflow reference
        assertEquals("org.test", runWorkflow.getWorkflow().getNamespace(), "Namespace should match");
        assertEquals("my-subflow", runWorkflow.getWorkflow().getName(), "Name should match");
        assertEquals("1.0.0", runWorkflow.getWorkflow().getVersion(), "Version should match");

        // Verify input (accessed through workflow config)
        assertNotNull(runWorkflow.getWorkflow().getInput(), "Input should be set");
        assertEquals(
                99,
                runWorkflow.getWorkflow().getInput().getAdditionalProperties().get("id"),
                "Input 'id' should match");

        // Verify await
        assertEquals(false, runWorkflow.isAwait(), "Await should be false");
    }

    @Test
    @DisplayName("subflow(name, workflow(...)) creates named subflow task with RunTask/RunWorkflow")
    void subflow_named_with_workflow_spec_creates_named_run_task() {
        Workflow wf = FlowWorkflowBuilder.workflow("parent-workflow")
                .tasks(
                        FlowDSL.subflow(
                                "mySubflowTask",
                                w -> w.namespace("org.acme")
                                        .name("child-workflow")
                                        .version("2.0.0")
                                        .input("key", "value")
                                        .await(true)))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        TaskItem taskItem = items.get(0);
        assertEquals("mySubflowTask", taskItem.getName(), "Task name should match");

        Task t = taskItem.getTask();
        assertNotNull(t.getRunTask(), "RunTask expected for subflow");

        RunWorkflow runWorkflow = t.getRunTask().getRun().getRunWorkflow();
        assertNotNull(runWorkflow, "RunWorkflow should be selected");
        assertEquals("org.acme", runWorkflow.getWorkflow().getNamespace());
        assertEquals("child-workflow", runWorkflow.getWorkflow().getName());
        assertEquals("2.0.0", runWorkflow.getWorkflow().getVersion());
        assertEquals(true, runWorkflow.isAwait(), "Await should be true");
    }

    @Test
    @DisplayName("grpc(name).proto().service().method().argument() builds CallGRPC task")
    void grpc_step_builds_call_grpc_task() {
        Workflow wf = FlowWorkflowBuilder.workflow("grpc-dsl-test")
                .tasks(
                        call(
                                "greet",
                                grpc("greet")
                                        .proto("proto/greeter.proto")
                                        .service("Greeter", "localhost")
                                        .method("SayHello")
                                        .argument("name", "World")))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());
        assertEquals("greet", items.get(0).getName());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask(), "CallTask expected for gRPC call");
        assertInstanceOf(CallGRPC.class, t.getCallTask().get(), "Should be CallGRPC");

        CallGRPC grpcArgs = (CallGRPC) t.getCallTask().get();
        assertNotNull(grpcArgs.getWith().getProto(), "Proto should be set");
        assertEquals("Greeter", grpcArgs.getWith().getService().getName());
        assertEquals("localhost", grpcArgs.getWith().getService().getHost());
        assertEquals("SayHello", grpcArgs.getWith().getMethod());
        assertEquals("World", grpcArgs.getWith().getArguments().getAdditionalProperties().get("name"));
    }

    @Test
    @DisplayName("call(grpc().proto().service().method().argument(...)) unnamed gRPC task")
    void grpc_unnamed_call_builds_task() {
        Workflow wf = FlowWorkflowBuilder.workflow("grpc-dsl-unnamed")
                .tasks(
                        call(
                                grpc()
                                        .proto("proto/svc.proto")
                                        .service("Svc", "host")
                                        .method("Call")
                                        .arguments(Map.of("k", "v"))))
                .build();

        List<TaskItem> items = wf.getDo();
        assertEquals(1, items.size());

        Task t = items.get(0).getTask();
        assertNotNull(t.getCallTask());
        assertInstanceOf(CallGRPC.class, t.getCallTask().get());
        assertEquals("Call", ((CallGRPC) t.getCallTask().get()).getWith().getMethod());
    }
}
