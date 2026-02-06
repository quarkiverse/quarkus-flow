package io.quarkiverse.flow;

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
        Class<? extends Flowable> clazz = this.getClass();
        String className = clazz.getName();
        if (className.endsWith("_Subclass") || className.endsWith("_ClientProxy")) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class && Flowable.class.isAssignableFrom(superclass)) {
                return superclass.getName();
            }
        }

        return className;
    }

}
