package io.quarkiverse.flow.langchain4j.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class CreativeWriterFlow extends Flow {

    @Inject
    CreativeWriterAdapter creativeWriter;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("creative-writer")
                .tasks(FlowDSL.function("writeStory",
                        (CreativeWriterRequest req) -> creativeWriter.writeStory(req)))
                .build();
    }
}
