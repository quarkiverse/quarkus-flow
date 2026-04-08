package io.quarkiverse.flow;

import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InterceptionProxySubclass;
import io.quarkus.arc.Subclass;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Interface for workflow definitions in Quarkus Flow.
 * <p>
 * This interface defines the contract for all workflow definitions. Users typically extend the {@link Flow} abstract class
 * rather than implementing this interface directly.
 * <p>
 * The interface provides core methods for workflow identification, descriptor access, and definition retrieval. It handles
 * Quarkus CDI proxy resolution to ensure consistent workflow identification across different bean contexts.
 *
 * @see Flow
 */
public interface Flowable {

    /**
     * Workflow descriptor you can describe via the CNCF Java SDK DSL.
     */
    Workflow descriptor();

    /**
     * Returns the unique workflow definition ID derived from the {@link #descriptor()}.
     * <p>
     * The ID is composed of the workflow's name, version, and namespace as defined in the descriptor.
     *
     * @return the workflow definition ID, never null
     */
    default WorkflowDefinitionId id() {
        return WorkflowDefinitionId.of(this.descriptor());
    }

    /**
     * Returns the fully qualified class name identifier for this flowable bean.
     * <p>
     * This method handles Quarkus CDI proxies and subclasses to return the actual implementation class name. It's used
     * internally by Quarkus Flow to uniquely identify workflow beans.
     *
     * @return the class name identifier, never null
     */
    default String identifier() {
        if (this instanceof ClientProxy proxy) {
            return proxy.getClass().getSuperclass().getName();
        } else if (this instanceof InterceptionProxySubclass proxy) {
            return proxy.getClass().getSuperclass().getName();
        } else if (this instanceof Subclass subclass) {
            return subclass.getClass().getSuperclass().getName();
        } else {
            return this.getClass().getName();
        }
    }

    /**
     * Returns the compiled workflow definition created at build time.
     * <p>
     * This is an advanced API for accessing low-level workflow metadata. For most use cases, prefer the convenience
     * methods provided by {@link Flow}.
     *
     * @return the workflow definition, never null
     * @see Flow#definition()
     * @see Flow#instance()
     * @see Flow#startInstance()
     */
    WorkflowDefinition definition();

}
