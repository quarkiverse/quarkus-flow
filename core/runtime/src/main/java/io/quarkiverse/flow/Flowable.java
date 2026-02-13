package io.quarkiverse.flow;

import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InterceptionProxySubclass;
import io.quarkus.arc.Subclass;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Users wants to extend {@link Flow}.
 */
public interface Flowable {

    /**
     * Workflow descriptor you can describe via the CNCF Java SDK DSL.
     */
    Workflow descriptor();

    default WorkflowDefinitionId id() {
        return WorkflowDefinitionId.of(this.descriptor());
    }

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

}
