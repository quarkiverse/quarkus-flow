package io.quarkiverse.flow;

import java.util.Map;

import jakarta.annotation.PostConstruct;

import io.quarkus.arc.Arc;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

/**
 * Base class for defining CNCF Serverless Workflows in Quarkus Flow.
 * <p>
 * Extend this class to create workflow definitions using the Java DSL from the CNCF Serverless Workflow SDK. Your workflow
 * class must be a CDI bean (typically {@code @ApplicationScoped}) and implement the {@link #descriptor()} method to define
 * the workflow structure.
 * <p>
 * Quarkus Flow processes workflow descriptors at build time, creating optimized {@link WorkflowDefinition} instances that
 * can be used to execute workflow instances at runtime. This approach provides compile-time validation and improved
 * performance.
 *
 * <h2>Basic Usage</h2>
 * <p>
 * Define a workflow by extending this class and implementing {@link #descriptor()}:
 *
 * <pre>
 * {@code
 * import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
 * import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;
 *
 * &#64;ApplicationScoped
 * public class CustomerProfileFlow extends Flow {
 *
 *     @Override
 *     public Workflow descriptor() {
 *         return workflow("customer-profile")
 *             .tasks(
 *                 get(URI.create("https://api.example.com/customer"))
 *                     .outputAs("${ { customer: .body } }")
 *             )
 *             .build();
 *     }
 * }
 * }
 * </pre>
 *
 * <h2>Executing Workflows</h2>
 * <p>
 * Inject your workflow bean and use the provided convenience methods to execute workflow instances:
 *
 * <pre>{@code
 * @Inject
 * CustomerProfileFlow customerFlow;
 *
 * // Quick execution with input (returns reactive Uni)
 * Uni<WorkflowModel> result = customerFlow.startInstance(Map.of("customerId", "123"));
 *
 * // Manual instance creation for more control
 * WorkflowInstance instance = customerFlow.instance(Map.of("customerId", "123"));
 * CompletionStage<WorkflowModel> stage = instance.start();
 * }</pre>
 *
 * <h2>Advanced: Accessing Workflow Metadata</h2>
 * <p>
 * For advanced use cases, the {@link #definition()} method provides access to the underlying workflow definition and
 * metadata:
 *
 * <pre>{@code
 * WorkflowDefinition def = customerFlow.definition();
 * String workflowName = def.workflow().getDocument().getName();
 * String version = def.workflow().getDocument().getVersion();
 * }</pre>
 *
 * <p>
 * <strong>Note:</strong> Most applications don't need direct access to the definition. Use the convenience methods
 * {@link #instance()} and {@link #startInstance()} instead.
 *
 * @see Flowable
 * @see WorkflowDefinition
 * @see WorkflowInstance
 * @see <a href="https://serverlessworkflow.io/">CNCF Serverless Workflow Specification</a>
 */
public abstract class Flow implements Flowable {

    private WorkflowDefinition definition;

    @PostConstruct
    void init() {
        this.definition = Arc.container()
                .select(WorkflowDefinition.class, Identifier.Literal.of(this.identifier()))
                .get();
    }

    /**
     * Defines the workflow structure using the CNCF Serverless Workflow Java DSL.
     * <p>
     * This is the core method you must implement when extending {@link Flow}. It defines your workflow's tasks, data flow,
     * error handling, and execution logic using the fluent DSL provided by the CNCF Serverless Workflow SDK.
     * <p>
     * The descriptor is evaluated once at build time. Quarkus Flow processes it to create an optimized
     * {@link WorkflowDefinition} that can be used to execute workflow instances at runtime.
     *
     * <h3>Basic Example</h3>
     *
     * <pre>{@code
     * @Override
     * public Workflow descriptor() {
     *     return workflow("order-processing")
     *             .tasks(
     *                     get(URI.create("https://api.example.com/orders/${.orderId}")),
     *                     call("sendConfirmation").with("$.email"))
     *             .build();
     * }
     * }</pre>
     *
     * <h3>Using Configuration</h3>
     * <p>
     * You can inject configuration properties to make your workflow descriptor dynamic:
     *
     * <pre>
     * {@code
     * &#64;ConfigProperty(name = "api.base-url")
     * String apiBaseUrl;
     *
     * @Override
     * public Workflow descriptor() {
     *     return workflow("customer-profile")
     *         .tasks(
     *             get(URI.create(apiBaseUrl + "/customer"))
     *         )
     *         .build();
     * }
     * }
     * </pre>
     *
     * <h3>Available Task Types</h3>
     * <p>
     * The DSL supports various task types including:
     * <ul>
     * <li>Data transformation: {@code set()}</li>
     * <li>CDI bean calls: {@code function()}, {@code withContext()}, {@code agent()}</li>
     * <li>HTTP requests: {@code get()}, {@code post()}, {@code http()}</li>
     * <li>OpenAPI operations: {@code openapi()}</li>
     * <li>Event messaging: {@code emitJson()}, {@code listen()}</li>
     * <li>Conditional branching: {@code switchCase()}, {@code switchWhen()}, {@code switchWhenOrElse()}</li>
     * <li>Iteration: {@code forEach()}</li>
     * <li>Fire-and-forget operations: {@code consume()}</li>
     * </ul>
     *
     * @return the workflow descriptor defining the workflow structure, must not be null
     * @see io.serverlessworkflow.fluent.func.FuncWorkflowBuilder
     * @see io.serverlessworkflow.fluent.func.dsl.FuncDSL
     */
    public abstract Workflow descriptor();

    /**
     * Returns the compiled workflow definition created at build time from the {@link #descriptor()}.
     * <p>
     * <strong>Note:</strong> This is an advanced API for accessing low-level workflow metadata and the SDK's
     * {@link WorkflowDefinition}. For most use cases, prefer the convenience methods {@link #instance()},
     * {@link #instance(Object)}, {@link #startInstance()}, or {@link #startInstance(Object)}.
     * <p>
     * The {@link WorkflowDefinition} provides access to:
     * <ul>
     * <li>Workflow metadata (name, version, namespace) via {@code definition().workflow().getDocument()}</li>
     * <li>Application context via {@code definition().application()}</li>
     * <li>Workflow ID via {@code definition().id()}</li>
     * <li>Low-level instance factory methods via {@code definition().instance()}</li>
     * </ul>
     * <p>
     * The definition is lazily initialized on first access and cached for subsequent calls.
     *
     * @return the workflow definition, never null
     * @see #instance()
     * @see #instance(Object)
     * @see #startInstance()
     */
    public final WorkflowDefinition definition() {
        if (definition != null) {
            return definition;
        }
        this.init();
        return definition;
    }

    /**
     * Creates a new workflow instance with empty input.
     * <p>
     * Use this method when your workflow doesn't require input data. The returned {@link WorkflowInstance} must be
     * explicitly started by calling {@link WorkflowInstance#start()}.
     *
     * <pre>{@code
     * WorkflowInstance instance = myFlow.instance();
     * CompletionStage<WorkflowModel> result = instance.start();
     * }</pre>
     *
     * @return a new workflow instance ready to be started, never null
     * @see #instance(Object)
     * @see #startInstance()
     */
    public WorkflowInstance instance() {
        return definition().instance(Map.of());
    }

    /**
     * Creates a new workflow instance with the provided input.
     * <p>
     * The input can be any object that will be serialized and made available to the workflow context. Common input types
     * include {@link Map}, POJOs, or any JSON-serializable object. The returned {@link WorkflowInstance} must be
     * explicitly started by calling {@link WorkflowInstance#start()}.
     *
     * <pre>{@code
     * Map<String, Object> input = Map.of("orderId", "12345", "customerId", "67890");
     * WorkflowInstance instance = myFlow.instance(input);
     * CompletionStage<WorkflowModel> result = instance.start();
     * }</pre>
     *
     * @param in the input data for the workflow instance, can be null
     * @return a new workflow instance ready to be started, never null
     * @see #instance()
     * @see #startInstance(Object)
     */
    public WorkflowInstance instance(Object in) {
        return definition().instance(in);
    }

    /**
     * Creates and immediately starts a new workflow instance with the provided input.
     * <p>
     * This is a convenience method that combines {@link #instance(Object)} and {@link WorkflowInstance#start()} into a
     * single call, returning a reactive {@link Uni} for non-blocking execution.
     *
     * <pre>{@code
     * Uni<WorkflowModel> result = myFlow.startInstance(Map.of("orderId", "12345"))
     *         .onItem().transform(model -> model.asMap().orElseThrow())
     *         .subscribe().with(output -> System.out.println("Result: " + output));
     * }</pre>
     *
     * @param in the input data for the workflow instance, can be null
     * @return a Uni that completes with the workflow model after execution, never null
     * @see #instance(Object)
     * @see #startInstance()
     */
    public Uni<WorkflowModel> startInstance(Object in) {
        return Uni.createFrom().completionStage(instance(in).start());
    }

    /**
     * Creates and immediately starts a new workflow instance with empty input.
     * <p>
     * This is a convenience method for workflows that don't require input data. Equivalent to calling
     * {@code startInstance(Map.of())}.
     *
     * <pre>{@code
     * Uni<WorkflowModel> result = myFlow.startInstance()
     *         .await().indefinitely();
     * }</pre>
     *
     * @return a Uni that completes with the workflow model after execution, never null
     * @see #instance()
     * @see #startInstance(Object)
     */
    public Uni<WorkflowModel> startInstance() {
        return startInstance(Map.of());
    }
}
