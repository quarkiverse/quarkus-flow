package io.quarkiverse.flow.langchain4j.spec;

import dev.langchain4j.agentic.scope.AgenticScope;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;

/**
 * WorkflowModelFactory that is AgenticScope-aware.
 * <p>
 * It wraps the default Jackson-based WorkflowModelFactory and only
 * intercepts LangChain4j AgenticScope inputs. Everything else is
 * delegated untouched.
 */
public class AgenticAwareModelFactory extends JacksonModelFactory {

    @Override
    public WorkflowModel fromOther(Object obj) {
        // Only intercept AgenticScope; everything else uses the parent's behavior
        if (obj instanceof AgenticScope scope) {
            // Delegate model is built from the *state* (Map) of the scope
            WorkflowModel delegateModel = super.from(scope.state());
            return new AgenticAwareWorkflowModel(scope, delegateModel);
        }

        return super.fromOther(obj);
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY - 1;
    }
}
